package com.codebox.agent.tool;

import com.codebox.agent.dto.ToolResult;
import com.codebox.agent.dto.ToolSpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * The set of tools the model is allowed to see and call.
 *
 * Tools are discovered from the Spring context, so adding a capability means adding
 * one bean - the loop itself never changes. The agent's power is therefore exactly the
 * registered set, with no generic escape hatch.
 */
@Component
public class ToolRegistry {

    private static final Logger log = LoggerFactory.getLogger(ToolRegistry.class);

    private final Map<String, AgentTool> tools = new LinkedHashMap<>();

    public ToolRegistry(List<AgentTool> discovered) {
        for (AgentTool tool : discovered) {
            String name = tool.spec().name();
            AgentTool previous = tools.put(name, tool);
            if (previous != null) {
                throw new IllegalStateException("Duplicate agent tool name: " + name);
            }
        }
        log.info("Registered {} agent tools: {}", tools.size(), tools.keySet());
    }

    public List<ToolSpec> specs() {
        return tools.values().stream().map(AgentTool::spec).toList();
    }

    public Optional<AgentTool> find(String name) {
        return Optional.ofNullable(tools.get(name));
    }

    public boolean isEmpty() {
        return tools.isEmpty();
    }

    /**
     * Looks up and runs a tool, converting any throwable into an error result so a
     * misbehaving tool degrades into a message the model can react to.
     */
    public ToolResult invoke(String name, String argumentsJson, ToolContext context) {
        Optional<AgentTool> found = find(name);
        if (found.isEmpty()) {
            return ToolResult.error("Unknown tool: " + name);
        }
        try {
            return found.get().execute(argumentsJson, context);
        } catch (Exception e) {
            log.warn("Tool {} threw: {}", name, e.toString());
            return ToolResult.error("Tool execution failed: " + e.getMessage());
        }
    }
}
