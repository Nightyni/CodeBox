package com.codebox.agent.dto;

/**
 * One observable step of an agent run.
 *
 * Produced from P1 onward even though the rich UI lands later: without it the whole
 * run is a black box, and neither the user nor a reviewer can tell whether the model
 * actually called a tool or simply made something up.
 *
 * @param type        MODEL (an LLM call) or TOOL_CALL (a tool execution)
 * @param tool        tool name for TOOL_CALL steps, otherwise null
 * @param detail      human-readable summary: answer excerpt, arguments, or error
 * @param elapsedMs   duration of this step
 */
public record AgentTraceStep(int step, String type, String tool, String detail, long elapsedMs) {

    public static AgentTraceStep model(int step, String detail, long elapsedMs) {
        return new AgentTraceStep(step, "MODEL", null, detail, elapsedMs);
    }

    public static AgentTraceStep tool(int step, String tool, String detail, long elapsedMs) {
        return new AgentTraceStep(step, "TOOL_CALL", tool, detail, elapsedMs);
    }
}
