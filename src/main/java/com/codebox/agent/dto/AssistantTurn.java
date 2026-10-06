package com.codebox.agent.dto;

import java.util.List;

/**
 * Result of one model call: either prose, or a request to run tools.
 *
 * Both can be present in principle (the model narrating while calling a tool),
 * so neither field is treated as exclusive.
 */
public record AssistantTurn(String content, List<ToolCall> toolCalls, boolean failed) {

    public static AssistantTurn text(String content) {
        return new AssistantTurn(content, List.of(), false);
    }

    public static AssistantTurn tools(List<ToolCall> toolCalls) {
        return new AssistantTurn(null, toolCalls, false);
    }

    public static AssistantTurn failure() {
        return new AssistantTurn(null, List.of(), true);
    }

    public boolean hasToolCalls() {
        return toolCalls != null && !toolCalls.isEmpty();
    }

    public boolean hasText() {
        return content != null && !content.isBlank();
    }
}
