package com.codebox.agent.tool;

/**
 * How dangerous a tool is.
 *
 * WRITE tools are never executed inside the agent loop - the loop stops and raises
 * a pending action that the user must approve. This is the human-in-the-loop gate.
 */
public enum RiskLevel {
    /** Reads business data or runs an LLM capability. Safe to auto-execute. */
    READ,
    /** Mutates business data. Requires explicit user confirmation. */
    WRITE
}
