package com.codebox.agent.dto;

import java.util.List;

/**
 * One entry in the model conversation.
 *
 * Mirrors the OpenAI/DeepSeek wire format: a tool result must carry the
 * {@code toolCallId} of the call it answers, and an assistant message that
 * requests tools must carry the {@code toolCalls} list back verbatim.
 */
public record ChatMessage(String role, String content, List<ToolCall> toolCalls, String toolCallId) {

    public static ChatMessage system(String content) {
        return new ChatMessage("system", content, null, null);
    }

    public static ChatMessage user(String content) {
        return new ChatMessage("user", content, null, null);
    }

    public static ChatMessage assistant(String content) {
        return new ChatMessage("assistant", content, null, null);
    }

    /** Replays an assistant turn that requested tools, so the model sees its own call. */
    public static ChatMessage assistantToolCalls(List<ToolCall> toolCalls) {
        return new ChatMessage("assistant", null, toolCalls, null);
    }

    /** Feeds a tool's output back to the model. */
    public static ChatMessage toolResult(String toolCallId, String content) {
        return new ChatMessage("tool", content, null, toolCallId);
    }
}
