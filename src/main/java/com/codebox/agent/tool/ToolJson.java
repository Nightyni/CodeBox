package com.codebox.agent.tool;

import com.codebox.agent.dto.ToolResult;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * JSON helpers shared by tool implementations.
 *
 * Tools speak JSON to the model, so every one of them needs "decode arguments" and
 * "encode result". Centralising it means each tool only contains its business logic,
 * and malformed model output is handled the same way everywhere.
 */
public final class ToolJson {

    private final ObjectMapper objectMapper;

    public ToolJson(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    /** @return the parsed arguments, or null when the raw text is not a JSON object. */
    public Map<String, Object> parse(String argumentsJson) {
        if (argumentsJson == null || argumentsJson.isBlank()) return Map.of();
        try {
            Map<String, Object> parsed = objectMapper.readValue(argumentsJson,
                    objectMapper.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
            return parsed == null ? Map.of() : parsed;
        } catch (Exception e) {
            return null;
        }
    }

    /** Serialises a payload to a JSON string for the model, falling back to toString. */
    public String write(Object payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (Exception e) {
            return String.valueOf(payload);
        }
    }

    public ToolResult ok(Object payload) {
        return ToolResult.ok(write(payload));
    }

    public static String asString(Object raw) {
        return raw == null ? null : raw.toString();
    }

    public static Long asLong(Object raw) {
        if (raw instanceof Number n) return n.longValue();
        if (raw instanceof String s) {
            try {
                return Long.parseLong(s.trim());
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    public static Map<String, Object> map() {
        return new LinkedHashMap<>();
    }
}
