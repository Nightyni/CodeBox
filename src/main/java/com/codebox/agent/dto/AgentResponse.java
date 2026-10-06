package com.codebox.agent.dto;

import java.util.List;

/**
 * Response of /api/agent/chat and /api/agent/confirm.
 *
 * @param answer        the assistant's reply (may be empty while awaiting approval)
 * @param trace         observable steps of the run
 * @param pendingAction non-null when the run stopped for user approval
 * @param history       conversation so far, to be sent back on the next turn
 * @param toolCallCount number of tools executed
 * @param steps         loop iterations consumed
 * @param elapsedMs     total wall time
 */
public record AgentResponse(String answer,
                            List<AgentTraceStep> trace,
                            PendingActionView pendingAction,
                            List<ChatMessage> history,
                            int toolCallCount,
                            int steps,
                            long elapsedMs) {
}
