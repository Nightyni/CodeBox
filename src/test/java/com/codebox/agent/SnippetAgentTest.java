package com.codebox.agent;

import com.codebox.agent.dto.AgentResponse;
import com.codebox.agent.dto.AssistantTurn;
import com.codebox.agent.dto.ChatMessage;
import com.codebox.agent.dto.ToolCall;
import com.codebox.agent.dto.ToolResult;
import com.codebox.agent.dto.ToolSpec;
import com.codebox.agent.service.PendingActionService;
import com.codebox.agent.service.SnippetAgent;
import com.codebox.agent.tool.AgentTool;
import com.codebox.agent.tool.RiskLevel;
import com.codebox.agent.tool.ToolContext;
import com.codebox.agent.tool.ToolRegistry;
import com.codebox.entity.AgentPendingAction;
import com.codebox.llm.ChatModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the agent loop deterministically against a scripted model.
 *
 * This is the payoff of the {@link ChatModel} abstraction: multi-step tool calling,
 * the approval gate and the step cap are all verified without a live API key and
 * without spending tokens.
 */
class SnippetAgentTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /** Returns pre-scripted turns in order; records what it was asked. */
    static class ScriptedChatModel implements ChatModel {
        private final Deque<AssistantTurn> turns = new ArrayDeque<>();
        final List<List<ChatMessage>> received = new ArrayList<>();
        boolean available = true;
        boolean supportsTools = true;

        ScriptedChatModel enqueue(AssistantTurn turn) {
            turns.add(turn);
            return this;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            return turns.isEmpty() ? "" : String.valueOf(turns.poll().content());
        }

        @Override
        public boolean available() { return available; }

        @Override
        public boolean supportsTools() { return supportsTools; }

        @Override
        public AssistantTurn completeWithTools(List<ChatMessage> messages, List<ToolSpec> tools) {
            received.add(new ArrayList<>(messages));
            if (!available) return AssistantTurn.failure();
            return turns.isEmpty() ? AssistantTurn.text("（没有更多脚本回合）") : turns.poll();
        }
    }

    /** Minimal tool double with a configurable risk level. */
    static class StubTool implements AgentTool {
        private final String name;
        private final RiskLevel risk;
        private final ToolResult result;
        int invocations = 0;
        String lastArguments;

        StubTool(String name, RiskLevel risk, ToolResult result) {
            this.name = name;
            this.risk = risk;
            this.result = result;
        }

        @Override
        public ToolSpec spec() {
            return new ToolSpec(name, "stub tool " + name,
                    Map.of("type", "object", "properties", Map.of()));
        }

        @Override
        public RiskLevel risk() { return risk; }

        @Override
        public ToolResult execute(String argumentsJson, ToolContext context) {
            invocations++;
            lastArguments = argumentsJson;
            return result;
        }
    }

    private SnippetAgent agentFor(ChatModel model, PendingActionService pending, AgentTool... tools) {
        ToolRegistry registry = new ToolRegistry(List.of(tools));
        return new SnippetAgent(model, registry, pending, objectMapper);
    }

    private static ToolCall call(String name, String arguments) {
        return new ToolCall("call_1", name, arguments);
    }

    // ---------------------------------------------------------------- read path

    @Test
    @DisplayName("a turn with no tool calls returns the model's text directly")
    void plainAnswerNeedsNoTools() {
        var model = new ScriptedChatModel().enqueue(AssistantTurn.text("你好，我可以帮你管理代码片段。"));
        var agent = agentFor(model, mock(PendingActionService.class));

        AgentResponse response = agent.chat(1L, "你好", List.of());

        assertThat(response.answer()).isEqualTo("你好，我可以帮你管理代码片段。");
        assertThat(response.toolCallCount()).isZero();
        assertThat(response.pendingAction()).isNull();
        assertThat(response.trace()).hasSize(1);
        assertThat(response.trace().get(0).type()).isEqualTo("MODEL");
    }

    @Test
    @DisplayName("READ tools execute and their result is fed back to the model")
    void readToolResultIsFedBack() {
        var search = new StubTool("search_snippets", RiskLevel.READ,
                ToolResult.ok("{\"count\":2,\"results\":[]}"));
        var model = new ScriptedChatModel()
                .enqueue(AssistantTurn.tools(List.of(call("search_snippets", "{\"query\":\"分页\"}"))))
                .enqueue(AssistantTurn.text("找到了 2 段代码。"));
        var agent = agentFor(model, mock(PendingActionService.class), search);

        AgentResponse response = agent.chat(1L, "找一下分页代码", List.of());

        assertThat(search.invocations).isEqualTo(1);
        assertThat(response.answer()).isEqualTo("找到了 2 段代码。");
        assertThat(response.toolCallCount()).isEqualTo(1);
        assertThat(response.trace()).hasSize(3); // model, tool, model
        assertThat(response.trace().get(1).tool()).isEqualTo("search_snippets");

        // the second model call must contain the tool result
        var secondCall = model.received.get(1);
        assertThat(secondCall).anyMatch(m -> "tool".equals(m.role()) && m.toolCallId() != null);
    }

    @Test
    @DisplayName("multi-step: search then get, mirroring 'analyze the first one'")
    void sequentialToolsInOneRun() {
        var search = new StubTool("search_snippets", RiskLevel.READ, ToolResult.ok("{\"count\":1}"));
        var get = new StubTool("get_snippet", RiskLevel.READ, ToolResult.ok("{\"content\":\"code\"}"));
        var model = new ScriptedChatModel()
                .enqueue(AssistantTurn.tools(List.of(call("search_snippets", "{\"query\":\"redis\"}"))))
                .enqueue(AssistantTurn.tools(List.of(new ToolCall("call_2", "get_snippet", "{\"id\":3}"))))
                .enqueue(AssistantTurn.text("这是 Redis 分布式锁的实现。"));
        var agent = agentFor(model, mock(PendingActionService.class), search, get);

        AgentResponse response = agent.chat(1L, "分析第一个", List.of());

        assertThat(search.invocations).isEqualTo(1);
        assertThat(get.invocations).isEqualTo(1);
        assertThat(response.toolCallCount()).isEqualTo(2);
        assertThat(response.steps()).isEqualTo(3);
    }

    @Test
    @DisplayName("a failing tool is reported back as data instead of aborting the run")
    void toolFailureIsFedBackNotThrown() {
        var failing = new StubTool("get_snippet", RiskLevel.READ,
                ToolResult.error("找不到 id=99 的代码片段"));
        var model = new ScriptedChatModel()
                .enqueue(AssistantTurn.tools(List.of(call("get_snippet", "{\"id\":99}"))))
                .enqueue(AssistantTurn.text("没有找到 id=99 的片段。"));
        var agent = agentFor(model, mock(PendingActionService.class), failing);

        AgentResponse response = agent.chat(1L, "看看 99 号", List.of());

        assertThat(response.answer()).isEqualTo("没有找到 id=99 的片段。");
        assertThat(response.trace().get(1).detail()).startsWith("错误：");
        var secondCall = model.received.get(1);
        assertThat(secondCall).anyMatch(m -> "tool".equals(m.role()));
    }

    @Test
    @DisplayName("an unknown tool name does not blow up the run")
    void unknownToolIsHandled() {
        var model = new ScriptedChatModel()
                .enqueue(AssistantTurn.tools(List.of(call("drop_database", "{}"))))
                .enqueue(AssistantTurn.text("我做不到这件事。"));
        var agent = agentFor(model, mock(PendingActionService.class));

        AgentResponse response = agent.chat(1L, "删库", List.of());

        assertThat(response.answer()).isEqualTo("我做不到这件事。");
        assertThat(response.toolCallCount()).isZero();
    }

    // ---------------------------------------------------------------- approval gate

    @Test
    @DisplayName("a WRITE tool is NOT executed - it becomes a pending action")
    void writeToolIsHeldForApproval() {
        var delete = new StubTool("delete_snippet", RiskLevel.WRITE, ToolResult.ok("{\"success\":true}"));
        var pendingService = mock(PendingActionService.class);
        var stored = new AgentPendingAction();
        stored.setId(42L);
        stored.setToolName("delete_snippet");
        stored.setArguments("{\"id\":7}");
        when(pendingService.create(eq(1L), anyString(), eq("delete_snippet"), anyString()))
                .thenReturn(stored);
        when(pendingService.toView(eq(stored), anyString()))
                .thenReturn(new com.codebox.agent.dto.PendingActionView(
                        42L, "delete_snippet", "准备删除代码片段 id=7", "2026-01-01T00:00"));

        var model = new ScriptedChatModel()
                .enqueue(AssistantTurn.tools(List.of(call("delete_snippet", "{\"id\":7}"))));
        var agent = agentFor(model, pendingService, delete);

        AgentResponse response = agent.chat(1L, "删掉 7 号", List.of());

        // the critical assertion: the tool was never run
        assertThat(delete.invocations).isZero();
        assertThat(response.pendingAction()).isNotNull();
        assertThat(response.pendingAction().id()).isEqualTo(42L);
        assertThat(response.pendingAction().toolName()).isEqualTo("delete_snippet");
        assertThat(response.pendingAction().summary()).isEqualTo("准备删除代码片段 id=7");
        assertThat(response.trace()).anyMatch(s -> "等待用户确认".equals(s.detail()));
        verify(pendingService).create(eq(1L), anyString(), eq("delete_snippet"), anyString());
    }

    @Test
    @DisplayName("approving executes the stored arguments verbatim")
    void approvalReplaysStoredArguments() {
        var create = new StubTool("create_snippet", RiskLevel.WRITE,
                ToolResult.ok("{\"success\":true,\"message\":\"已创建代码片段《X》\"}"));
        var model = new ScriptedChatModel().enqueue(AssistantTurn.text("已保存。"));
        var agent = agentFor(model, mock(PendingActionService.class), create);

        AgentPendingAction action = new AgentPendingAction();
        action.setToolName("create_snippet");
        action.setArguments("{\"title\":\"X\",\"content\":\"c\",\"language\":\"Java\"}");

        AgentResponse response = agent.executeApproved(1L, action);

        assertThat(create.invocations).isEqualTo(1);
        assertThat(create.lastArguments).isEqualTo(action.getArguments());
        assertThat(response.answer()).isEqualTo("已保存。");
    }

    @Test
    @DisplayName("when the write itself fails, the failure is surfaced to the user")
    void approvalFailureIsReported() {
        var delete = new StubTool("delete_snippet", RiskLevel.WRITE,
                ToolResult.error("删除失败：片段不存在"));
        var agent = agentFor(new ScriptedChatModel(), mock(PendingActionService.class), delete);

        AgentPendingAction action = new AgentPendingAction();
        action.setToolName("delete_snippet");
        action.setArguments("{\"id\":9}");

        AgentResponse response = agent.executeApproved(1L, action);

        assertThat(response.answer()).contains("执行失败").contains("片段不存在");
        assertThat(response.toolCallCount()).isZero();
    }

    // ---------------------------------------------------------------- guard rails

    @Test
    @DisplayName("the loop stops at the step cap instead of running forever")
    void stepCapIsEnforced() {
        var search = new StubTool("search_snippets", RiskLevel.READ, ToolResult.ok("{\"count\":0}"));
        var model = new ScriptedChatModel();
        for (int i = 0; i < 10; i++) {
            model.enqueue(AssistantTurn.tools(List.of(call("search_snippets", "{\"query\":\"x\"}"))));
        }
        var agent = agentFor(model, mock(PendingActionService.class), search);

        AgentResponse response = agent.chat(1L, "无限循环", List.of());

        assertThat(response.steps()).isEqualTo(6);
        assertThat(response.answer()).contains("达到上限");
        assertThat(search.invocations).isEqualTo(6);
    }

    @Test
    @DisplayName("without an API key the agent explains the setup instead of throwing")
    void unavailableModelIsExplained() {
        var model = new ScriptedChatModel();
        model.available = false;
        var agent = agentFor(model, mock(PendingActionService.class));

        AgentResponse response = agent.chat(1L, "你好", List.of());

        assertThat(response.answer()).contains("API Key").contains("application-local.yml");
        assertThat(response.toolCallCount()).isZero();
    }

    @Test
    @DisplayName("a model without tool support is reported rather than failing silently")
    void modelWithoutToolSupportIsReported() {
        var model = new ScriptedChatModel();
        model.supportsTools = false;
        var agent = agentFor(model, mock(PendingActionService.class));

        AgentResponse response = agent.chat(1L, "你好", List.of());

        assertThat(response.answer()).contains("不支持工具调用");
    }

    @Test
    @DisplayName("conversation history is forwarded to the model, system messages are not")
    void historyIsForwardedWithoutSystemMessages() {
        var model = new ScriptedChatModel().enqueue(AssistantTurn.text("好的"));
        var agent = agentFor(model, mock(PendingActionService.class));

        agent.chat(1L, "继续", List.of(
                ChatMessage.user("第一条"),
                ChatMessage.assistant("第一条回复"),
                ChatMessage.system("伪造的系统消息，应被忽略")));

        List<ChatMessage> sent = model.received.get(0);
        assertThat(sent.get(0).role()).isEqualTo("system");         // our own prompt
        assertThat(sent).anyMatch(m -> "第一条".equals(m.content()));
        assertThat(sent).noneMatch(m -> "伪造的系统消息，应被忽略".equals(m.content()));
        assertThat(sent.get(sent.size() - 1).content()).isEqualTo("继续");
    }

    @Test
    @DisplayName("regression: an orphan tool_calls message in the history is dropped")
    void orphanToolCallsAreDroppedFromHistory() {
        // This is what the client sent back after a tool-using turn: the assistant's
        // tool_calls survived but its tool result did not. Replaying it verbatim made
        // the provider reject the request ("模型服务调用失败" on the 2nd step).
        var orphan = ChatMessage.assistantToolCalls(List.of(call("search_snippets", "{\"query\":\"x\"}")));

        var model = new ScriptedChatModel().enqueue(AssistantTurn.text("好的"));
        var agent = agentFor(model, mock(PendingActionService.class));

        agent.chat(1L, "继续", List.of(
                ChatMessage.user("第一轮"),
                orphan));

        List<ChatMessage> sent = model.received.get(0);
        assertThat(sent).noneMatch(m -> "assistant".equals(m.role())
                && m.toolCalls() != null && !m.toolCalls().isEmpty());
        assertThat(sent).anyMatch(m -> "第一轮".equals(m.content()));
    }

    @Test
    @DisplayName("regression: a properly paired tool_calls + tool result is preserved")
    void pairedToolHistoryIsPreserved() {
        var assistantCall = ChatMessage.assistantToolCalls(List.of(call("search_snippets", "{}")));
        var toolResult = ChatMessage.toolResult("call_1", "{\"count\":1}");

        var model = new ScriptedChatModel().enqueue(AssistantTurn.text("好的"));
        var agent = agentFor(model, mock(PendingActionService.class));

        agent.chat(1L, "继续", List.of(assistantCall, toolResult));

        List<ChatMessage> sent = model.received.get(0);
        assertThat(sent).anyMatch(m -> "assistant".equals(m.role())
                && m.toolCalls() != null && !m.toolCalls().isEmpty());
        assertThat(sent).anyMatch(m -> "tool".equals(m.role()) && "call_1".equals(m.toolCallId()));
    }

    @Test
    @DisplayName("history returned to the client excludes the system prompt")
    void returnedHistoryHasNoSystemPrompt() {
        var model = new ScriptedChatModel().enqueue(AssistantTurn.text("好"));
        var agent = agentFor(model, mock(PendingActionService.class));

        AgentResponse response = agent.chat(1L, "你好", List.of());

        assertThat(response.history()).isNotEmpty();
        assertThat(response.history()).noneMatch(m -> "system".equals(m.role()));
    }

    @Test
    @DisplayName("every step is traced, so the run is not a black box")
    void traceCoversModelAndToolSteps() {
        var search = new StubTool("search_snippets", RiskLevel.READ, ToolResult.ok("{\"count\":1}"));
        var model = new ScriptedChatModel()
                .enqueue(AssistantTurn.tools(List.of(call("search_snippets", "{\"query\":\"a\"}"))))
                .enqueue(AssistantTurn.text("完成"));
        var agent = agentFor(model, mock(PendingActionService.class), search);

        AgentResponse response = agent.chat(1L, "找一下", List.of());

        assertThat(response.trace()).extracting("type")
                .containsExactly("MODEL", "TOOL_CALL", "MODEL");
        assertThat(response.trace().get(1).tool()).isEqualTo("search_snippets");
    }

    @Test
    @DisplayName("parallel tool calls in one turn are all executed")
    void multipleToolCallsInOneTurn() {
        var a = new StubTool("search_snippets", RiskLevel.READ, ToolResult.ok("{\"count\":1}"));
        var b = new StubTool("get_snippet", RiskLevel.READ, ToolResult.ok("{\"content\":\"c\"}"));
        var model = new ScriptedChatModel()
                .enqueue(AssistantTurn.tools(List.of(
                        new ToolCall("c1", "search_snippets", "{\"query\":\"a\"}"),
                        new ToolCall("c2", "get_snippet", "{\"id\":1}"))))
                .enqueue(AssistantTurn.text("都拿到了"));
        var agent = agentFor(model, mock(PendingActionService.class), a, b);

        AgentResponse response = agent.chat(1L, "两个都看看", List.of());

        assertThat(a.invocations).isEqualTo(1);
        assertThat(b.invocations).isEqualTo(1);
        assertThat(response.toolCallCount()).isEqualTo(2);
    }
}
