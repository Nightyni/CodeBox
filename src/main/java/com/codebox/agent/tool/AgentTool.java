package com.codebox.agent.tool;

import com.codebox.agent.dto.ToolResult;
import com.codebox.agent.dto.ToolSpec;

/**
 * A capability the agent may invoke.
 *
 * The agent's power is exactly the union of the registered tools - there is no
 * generic "run SQL" escape hatch, so business boundaries are enforced by
 * construction rather than by prompt wording.
 */
public interface AgentTool {

    ToolSpec spec();

    RiskLevel risk();

    /**
     * Runs the tool.
     *
     * Implementations must validate {@code argumentsJson} themselves and return
     * {@link ToolResult#error} for bad input instead of throwing.
     *
     * @param argumentsJson raw JSON object produced by the model
     * @param context       server-side context carrying the authenticated user
     */
    ToolResult execute(String argumentsJson, ToolContext context);
}
