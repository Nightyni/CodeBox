package com.codebox.agent.tool;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Minimal JSON Schema builder.
 *
 * Hand-writing {@code Map.of(...)} literals for seven tools is unreadable and easy to
 * get wrong, and pulling in a schema library for this is overkill. This keeps the
 * tool definitions declarative.
 */
public final class ToolSchema {

    private ToolSchema() {}

    public static Map<String, Object> object(Map<String, Object> properties, List<String> required) {
        Map<String, Object> schema = new LinkedHashMap<>();
        schema.put("type", "object");
        schema.put("properties", properties);
        schema.put("required", required);
        schema.put("additionalProperties", false);
        return schema;
    }

    public static Map<String, Object> string(String description) {
        return Map.of("type", "string", "description", description);
    }

    public static Map<String, Object> integer(String description) {
        return Map.of("type", "integer", "description", description);
    }

    /** Collects properties in declaration order. */
    public static final class Props {
        private final Map<String, Object> map = new LinkedHashMap<>();

        public Props put(String name, Map<String, Object> schema) {
            map.put(name, schema);
            return this;
        }

        public Map<String, Object> build() {
            return map;
        }
    }

    public static Props props() {
        return new Props();
    }
}
