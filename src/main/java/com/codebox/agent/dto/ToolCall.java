package com.codebox.agent.dto;

/**
 * A tool invocation requested by the model.
 *
 * @param id        provider-assigned call id; must be echoed back with the result
 * @param name      tool name, resolved against the registry
 * @param arguments raw JSON object produced by the model - always validated before use
 */
public record ToolCall(String id, String name, String arguments) {
}
