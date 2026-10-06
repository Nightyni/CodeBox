package com.codebox.agent.dto;

import java.util.Map;

/**
 * Description of a tool exposed to the model.
 *
 * @param name        unique tool name
 * @param description what the tool does, in the model's language
 * @param parameters  JSON Schema for the arguments object
 */
public record ToolSpec(String name, String description, Map<String, Object> parameters) {
}
