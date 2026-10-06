package com.codebox.llm;

import com.codebox.agent.dto.AssistantTurn;
import com.codebox.agent.dto.ChatMessage;
import com.codebox.agent.dto.ToolCall;
import com.codebox.agent.dto.ToolSpec;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.http.client.ClientHttpRequestFactoryBuilder;
import org.springframework.boot.http.client.ClientHttpRequestFactorySettings;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * DeepSeek chat client (OpenAI-compatible /chat/completions).
 *
 * Returns an empty string when the model is unconfigured or the call fails, so a
 * model outage degrades the feature instead of breaking the whole request.
 */
@Component
public class DeepSeekChatModel implements ChatModel {

    private static final Logger log = LoggerFactory.getLogger(DeepSeekChatModel.class);

    private final LlmProperties properties;
    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private volatile boolean lastCallFailed = false;

    public DeepSeekChatModel(LlmProperties properties, RestClient.Builder builder, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        var settings = ClientHttpRequestFactorySettings.defaults()
                .withConnectTimeout(Duration.ofSeconds(10))
                .withReadTimeout(Duration.ofSeconds(Math.max(10, properties.getTimeoutSeconds())));
        this.restClient = builder
                .baseUrl(properties.getBaseUrl())
                .requestFactory(ClientHttpRequestFactoryBuilder.detect().build(settings))
                .build();
    }

    @Override
    public boolean available() {
        return properties.hasChatKey();
    }

    @Override
    public String complete(String systemPrompt, String userPrompt) {
        if (!available()) {
            log.debug("LLM disabled or API key missing; skipping call");
            return "";
        }
        lastCallFailed = false;
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of(
                            "model", properties.getModel(),
                            "messages", List.of(
                                    Map.of("role", "system", "content", systemPrompt),
                                    Map.of("role", "user", "content", userPrompt)),
                            "temperature", 0.2,
                            "stream", false))
                    .retrieve()
                    .body(Map.class);

            return extractContent(response);
        } catch (Exception e) {
            // Distinguish "the call failed" from "the model declined to answer":
            // the caller reports them to the user very differently.
            lastCallFailed = true;
            log.warn("LLM call failed, degrading gracefully: {}", e.toString());
            return "";
        }
    }

    @Override
    public boolean supportsTools() {
        return true;
    }

    @Override
    public AssistantTurn completeWithTools(List<ChatMessage> messages, List<ToolSpec> tools) {
        if (!available()) {
            return AssistantTurn.failure();
        }
        lastCallFailed = false;
        try {
            Map<String, Object> payload = new LinkedHashMap<>();
            payload.put("model", properties.getModel());
            payload.put("messages", messages.stream().map(this::toWireMessage).toList());
            payload.put("temperature", 0.2);
            payload.put("stream", false);
            if (tools != null && !tools.isEmpty()) {
                payload.put("tools", tools.stream().map(this::toWireTool).toList());
                payload.put("tool_choice", "auto");
            }

            @SuppressWarnings("unchecked")
            Map<String, Object> response = restClient.post()
                    .uri("/chat/completions")
                    .header("Authorization", "Bearer " + properties.getApiKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(payload)
                    .retrieve()
                    .body(Map.class);

            return parseTurn(response);
        } catch (Exception e) {
            lastCallFailed = true;
            log.warn("LLM tool call failed: {}", e.toString());
            return AssistantTurn.failure();
        }
    }

    @Override
    public boolean lastCallFailed() {
        return lastCallFailed;
    }

    // ---------------------------------------------------------------- wire format

    private Map<String, Object> toWireMessage(ChatMessage message) {
        Map<String, Object> wire = new LinkedHashMap<>();
        wire.put("role", message.role());

        // "content" must ALWAYS be present on assistant messages, even when null
        // (the tool-calling turn carries no prose). Omitting the key makes the
        // provider reject the whole request, which surfaced as
        // "模型服务调用失败" on the SECOND step of every tool run.
        wire.put("content", message.content());

        if (message.toolCallId() != null) {
            wire.put("tool_call_id", message.toolCallId());
        }
        if (message.toolCalls() != null && !message.toolCalls().isEmpty()) {
            wire.put("tool_calls", message.toolCalls().stream().map(call -> {
                Map<String, Object> fn = new LinkedHashMap<>();
                fn.put("name", call.name());
                fn.put("arguments", call.arguments() == null ? "{}" : call.arguments());
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("id", call.id());
                entry.put("type", "function");
                entry.put("function", fn);
                return entry;
            }).toList());
        }
        return wire;
    }

    private Map<String, Object> toWireTool(ToolSpec spec) {
        Map<String, Object> fn = new LinkedHashMap<>();
        fn.put("name", spec.name());
        fn.put("description", spec.description());
        fn.put("parameters", spec.parameters());
        return Map.of("type", "function", "function", fn);
    }

    @SuppressWarnings("unchecked")
    private AssistantTurn parseTurn(Map<String, Object> response) {
        if (response == null) return AssistantTurn.failure();
        Object choices = response.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) return AssistantTurn.failure();
        if (!(list.get(0) instanceof Map<?, ?> choice)) return AssistantTurn.failure();
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> msg)) return AssistantTurn.failure();

        String content = msg.get("content") == null ? null : msg.get("content").toString();

        List<ToolCall> calls = new ArrayList<>();
        Object toolCalls = msg.get("tool_calls");
        if (toolCalls instanceof List<?> callList) {
            for (Object raw : callList) {
                if (!(raw instanceof Map<?, ?> call)) continue;
                Object id = call.get("id");
                Object function = call.get("function");
                if (!(function instanceof Map<?, ?> fn)) continue;
                String name = fn.get("name") == null ? null : fn.get("name").toString();
                if (name == null) continue;
                calls.add(new ToolCall(
                        id == null ? "call_" + calls.size() : id.toString(),
                        name,
                        normalizeArguments(fn.get("arguments"))));
            }
        }

        if (calls.isEmpty()) {
            return content == null ? AssistantTurn.failure() : AssistantTurn.text(content);
        }
        return new AssistantTurn(content, calls, false);
    }

    /**
     * The provider may return arguments either as a JSON string or, in some
     * gateways, as an already-decoded object. Normalise to a JSON string so the
     * rest of the code has a single representation to deal with.
     */
    private String normalizeArguments(Object arguments) {
        if (arguments == null) return "{}";
        if (arguments instanceof String s) {
            return s.isBlank() ? "{}" : s;
        }
        try {
            return objectMapper.writeValueAsString(arguments);
        } catch (Exception e) {
            log.warn("Could not re-serialise tool arguments: {}", e.toString());
            return "{}";
        }
    }

    @SuppressWarnings("unchecked")
    private String extractContent(Map<String, Object> response) {
        if (response == null) return "";
        Object choices = response.get("choices");
        if (!(choices instanceof List<?> list) || list.isEmpty()) return "";
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> choice)) return "";
        Object message = choice.get("message");
        if (!(message instanceof Map<?, ?> msg)) return "";
        Object content = msg.get("content");
        return content == null ? "" : content.toString();
    }
}
