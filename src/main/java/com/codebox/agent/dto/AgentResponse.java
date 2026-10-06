package com.codebox.agent.dto;

import java.util.List;

/**
 * Result of one /api/agent/chat turn.
 *
 * @param answer        the assistant's reply (may be empty when approval is pending)
 * @param trace         observable steps of the run
 * @param pendingAction non-null when the run stopped to ask for approval
 * @param history       conversation so far, to be sent back on the next turn
 * @param toolResults   successful tool outputs, so the client can render rich views
 *                      (e.g. the diff produced by optimize_snippet) without re-asking
 *                      the model or re-running the tool
 * @param toolCallCount number of tools executed
 * @param steps         loop iterations consumed
 * @param elapsedMs     total wall time
 */
public record AgentResponse(String answer,
                            List<AgentTraceStep> trace,
                            PendingActionView pendingAction,
                            List<ChatMessage> history,
                            List<ToolResultBlock> toolResults,
                            int toolCallCount,
                            int steps,
                            long elapsedMs) {

    /**
     * A tool's output with its name.
     *
     * @param tool   tool name
     * @param result the parsed JSON the tool returned; null when it was not JSON
     * @param text   the raw text, kept so a non-JSON result is never lost
     */
    public record ToolResultBlock(String tool, Object result, String text) {}
}