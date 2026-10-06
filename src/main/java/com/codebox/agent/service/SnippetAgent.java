package com.codebox.agent.service;

import com.codebox.agent.dto.AgentResponse;
import com.codebox.agent.dto.AgentTraceStep;
import com.codebox.agent.dto.ChatMessage;
import com.codebox.agent.dto.PendingActionView;
import com.codebox.agent.dto.ToolCall;
import com.codebox.agent.dto.ToolResult;
import com.codebox.agent.tool.AgentTool;
import com.codebox.agent.tool.RiskLevel;
import com.codebox.agent.tool.ToolContext;
import com.codebox.agent.tool.ToolRegistry;
import com.codebox.entity.AgentPendingAction;
import com.codebox.llm.ChatModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Set;
import java.util.Map;

/**
 * The agent loop: model -> tools -> model, until it produces a final answer or hits a
 * guard rail.
 *
 * Guard rails:
 *  - a hard step cap, so a model that keeps calling tools cannot loop forever
 *  - WRITE tools are never executed here; they are captured as a pending action and
 *    the loop stops, which is what makes approval meaningful
 *  - tool failures are fed back as data, so the model can recover or explain instead
 *    of the whole turn exploding
 */
@Service
public class SnippetAgent {

    private static final Logger log = LoggerFactory.getLogger(SnippetAgent.class);

    /** Maximum model/tool round trips per user turn. */
    private static final int MAX_STEPS = 6;
    private static final int MAX_RESULT_CHARS = 4000;
    private static final int MAX_HISTORY_MESSAGE_CHARS = 2000;

    private final ChatModel chatModel;
    private final ToolRegistry toolRegistry;
    private final PendingActionService pendingActionService;
    private final ObjectMapper objectMapper;

    public SnippetAgent(ChatModel chatModel,
                        ToolRegistry toolRegistry,
                        PendingActionService pendingActionService,
                        ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.toolRegistry = toolRegistry;
        this.pendingActionService = pendingActionService;
        this.objectMapper = objectMapper;
    }

    public AgentResponse chat(Long userId, String userMessage, List<ChatMessage> history) {
        long start = System.currentTimeMillis();
        ToolContext context = new ToolContext(userId);

        List<ChatMessage> messages = new ArrayList<>();
        messages.add(ChatMessage.system(AgentPrompt.SYSTEM));
        messages.addAll(sanitizeHistory(history));
        messages.add(ChatMessage.user(userMessage));

        if (!chatModel.available()) {
            return new AgentResponse(
                    "还没有配置大模型 API Key，AI 助手无法使用。\n"
                            + "把 key 填到 config/application-local.yml 的 codebox.llm.api-key 后重启即可。",
                    List.of(), null, tail(messages), List.of(), 0, 0, elapsed(start));
        }
        if (!chatModel.supportsTools()) {
            return new AgentResponse(
                    "当前模型不支持工具调用，无法使用 AI 助手（需要支持 function calling 的模型）。",
                    List.of(), null, tail(messages), List.of(), 0, 0, elapsed(start));
        }

        List<AgentTraceStep> trace = new ArrayList<>();
        List<AgentResponse.ToolResultBlock> toolResults = new ArrayList<>();
        int toolCallCount = 0;

        for (int step = 1; step <= MAX_STEPS; step++) {
            var turn = chatModel.completeWithTools(messages, toolRegistry.specs());

            if (turn.failed()) {
                trace.add(AgentTraceStep.model(step, "模型调用失败", elapsed(start)));
                String answer = trace.isEmpty()
                        ? AgentPrompt.TOOL_OUTAGE_NOTE
                        : "抱歉，模型服务调用失败，本轮未能完成。请稍后重试。";
                return new AgentResponse(answer, trace, null, tail(messages), toolResults,
                        toolCallCount, step, elapsed(start));
            }

            trace.add(AgentTraceStep.model(step,
                    turn.hasText() ? preview(turn.content()) : "请求调用 " + turn.toolCalls().size() + " 个工具",
                    elapsed(start)));

            if (!turn.hasToolCalls()) {
                return new AgentResponse(
                        turn.hasText() ? turn.content().strip() : "（模型没有返回内容）",
                        trace, null, tail(messages), toolResults, toolCallCount, step, elapsed(start));
            }

            messages.add(ChatMessage.assistantToolCalls(turn.toolCalls()));

            for (ToolCall call : turn.toolCalls()) {
                var tool = toolRegistry.find(call.name());
                if (tool.isEmpty()) {
                    messages.add(ChatMessage.toolResult(call.id(),
                            "错误：不存在名为 " + call.name() + " 的工具。"));
                    trace.add(AgentTraceStep.tool(step, call.name(), "未知工具", 0));
                    continue;
                }

                // Human-in-the-loop: capture the write, do not perform it.
                if (tool.get().risk() == RiskLevel.WRITE) {
                    AgentPendingAction pending = pendingActionService.create(
                            userId, call.id(), call.name(), call.arguments());
                    String summary = summarise(tool.get(), call.arguments());
                    trace.add(AgentTraceStep.tool(step, call.name(), "等待用户确认", 0));
                    log.info("Agent stopped for approval: user={} tool={}", userId, call.name());

                    String answer = turn.hasText() && !turn.content().isBlank()
                            ? turn.content().strip()
                            : summary;
                    return new AgentResponse(answer, trace,
                            pendingActionService.toView(pending, summary),
                            tail(messages), toolResults, toolCallCount, step, elapsed(start));
                }

                long toolStart = System.currentTimeMillis();
                ToolResult result = toolRegistry.invoke(call.name(), call.arguments(), context);
                long toolElapsed = System.currentTimeMillis() - toolStart;
                toolCallCount++;

                trace.add(AgentTraceStep.tool(step, call.name(),
                        result.success() ? preview(result.text()) : "错误：" + preview(result.text()),
                        toolElapsed));
                if (result.success()) {
                    toolResults.add(toBlock(call.name(), result.text()));
                }
                messages.add(ChatMessage.toolResult(call.id(), truncate(result.text())));
            }
        }

        trace.add(AgentTraceStep.model(MAX_STEPS + 1, "达到步数上限", elapsed(start)));
        return new AgentResponse(
                "这次请求需要的步骤过多，已达到上限（" + MAX_STEPS + " 步）而停止。"
                        + "请把需求拆得更具体一些，例如直接说明要操作哪一条代码片段。",
                trace, null, tail(messages), toolResults, toolCallCount, MAX_STEPS, elapsed(start));
    }

    /**
     * Cleans client-supplied history before it is replayed to the model.
     *
     * A tool-calling turn is only valid as the PAIR "assistant(tool_calls)" +
     * "tool(result)". If the client kept only the conversational turns (which is
     * exactly what the frontend does), the assistant half survives on its own and
     * the provider rejects the request - so any unpaired call is dropped here.
     */
    private List<ChatMessage> sanitizeHistory(List<ChatMessage> history) {
        List<ChatMessage> out = new ArrayList<>();
        if (history == null) return out;

        for (ChatMessage past : history) {
            if (past == null || past.role() == null || "system".equals(past.role())) continue;
            out.add(clampMessage(past));
        }

        // Collect the tool_call ids that actually have a matching result.
        Set<String> answered = new HashSet<>();
        for (ChatMessage m : out) {
            if ("tool".equals(m.role()) && m.toolCallId() != null) answered.add(m.toolCallId());
        }
        out.removeIf(m -> "assistant".equals(m.role())
                && m.toolCalls() != null && !m.toolCalls().isEmpty()
                && m.toolCalls().stream().anyMatch(c -> !answered.contains(c.id())));

        // A tool result without its request is equally invalid.
        out.removeIf(m -> "tool".equals(m.role()) && m.toolCallId() == null);
        return out;
    }

    /**
     * Runs a previously captured write action on the user's behalf.
     *
     * @param action the action already claimed and ownership-verified by the caller
     */
    public AgentResponse executeApproved(Long userId, AgentPendingAction action) {
        long start = System.currentTimeMillis();
        ToolContext context = new ToolContext(userId);
        List<AgentTraceStep> trace = new ArrayList<>();

        ToolResult result = toolRegistry.invoke(action.getToolName(), action.getArguments(), context);
        trace.add(AgentTraceStep.tool(1, action.getToolName(),
                result.success() ? "已执行" : "执行失败", elapsed(start)));

        String answer;
        if (!result.success()) {
            answer = "执行失败：" + result.text();
        } else if (chatModel.available() && chatModel.supportsTools()) {
            // Let the model phrase the outcome naturally; fall back to the raw text.
            List<ChatMessage> messages = new ArrayList<>();
            messages.add(ChatMessage.system(AgentPrompt.SYSTEM));
            messages.add(ChatMessage.user("用户已确认并执行了写操作 " + action.getToolName()
                    + "。工具返回结果如下，请用一句中文告诉用户结果：\n" + truncate(result.text())));
            var turn = chatModel.completeWithTools(messages, toolRegistry.specs());
            answer = turn.hasText() ? turn.content().strip()
                    : "操作已完成：" + extractMessage(result.text());
        } else {
            answer = "操作已完成：" + extractMessage(result.text());
        }

        return new AgentResponse(answer, trace, null,
                List.of(ChatMessage.user("(已确认执行 " + action.getToolName() + ")")), List.of(),
                result.success() ? 1 : 0, 1, elapsed(start));
    }

    // ---------------------------------------------------------------- helpers

    private String summarise(AgentTool tool, String argumentsJson) {
        Map<String, Object> args = parseArguments(argumentsJson);
        String name = tool.spec().name();
        if (args == null) return "准备执行 " + name;

        return switch (name) {
            case "create_snippet" -> "准备创建代码片段《" + args.getOrDefault("title", "未命名") + "》";
            case "update_snippet" -> "准备修改代码片段 id=" + args.get("id");
            case "delete_snippet" -> "准备删除代码片段 id=" + args.get("id");
            default -> "准备执行 " + name;
        };
    }

    /** Tolerant parse: a malformed arguments blob must not break the approval prompt. */
    private Map<String, Object> parseArguments(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) return Map.of();
        try {
            Map<String, Object> parsed = objectMapper.readValue(argumentsJson,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            return null;
        }
    }

    /**
     * Wraps a tool's JSON output for the client.
     *
     * The parsed form is what the UI renders (the diff view needs structure), while the
     * raw text is kept so a non-JSON result is never lost.
     */
    private AgentResponse.ToolResultBlock toBlock(String toolName, String text) {
        try {
            Object parsed = objectMapper.readValue(text, Object.class);
            return new AgentResponse.ToolResultBlock(toolName, parsed, text);
        } catch (Exception e) {
            return new AgentResponse.ToolResultBlock(toolName, null, text);
        }
    }
    /** Best-effort read of the human-readable "message" field a write tool returns. */
    private String extractMessage(String json) {
        try {
            Map<?, ?> parsed = objectMapper.readValue(json, Map.class);
            Object message = parsed.get("message");
            return message == null ? json : message.toString();
        } catch (Exception e) {
            return json;
        }
    }

    private ChatMessage clampMessage(ChatMessage message) {
        if (message.content() == null) return message;
        if (message.content().length() <= MAX_HISTORY_MESSAGE_CHARS) return message;
        return new ChatMessage(message.role(),
                message.content().substring(0, MAX_HISTORY_MESSAGE_CHARS) + "...",
                message.toolCalls(), message.toolCallId());
    }

    /** Keeps the returned history small: the client only needs recent context. */
    private List<ChatMessage> tail(List<ChatMessage> messages) {
        List<ChatMessage> out = new ArrayList<>();
        for (ChatMessage message : messages) {
            if ("system".equals(message.role())) continue;
            out.add(clampMessage(message));
        }
        int limit = 40;
        return out.size() <= limit ? out : new ArrayList<>(out.subList(out.size() - limit, out.size()));
    }

    private static String truncate(String text) {
        if (text == null) return "";
        return text.length() <= MAX_RESULT_CHARS ? text : text.substring(0, MAX_RESULT_CHARS) + "...(已截断)";
    }

    private static String preview(String text) {
        if (text == null) return "";
        String flat = text.replaceAll("\\s+", " ").strip();
        return flat.length() <= 120 ? flat : flat.substring(0, 120) + "...";
    }

    private static long elapsed(long start) {
        return System.currentTimeMillis() - start;
    }
}
