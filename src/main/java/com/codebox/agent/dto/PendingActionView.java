package com.codebox.agent.dto;

/**
 * A write operation the agent wants to perform, surfaced to the user for approval.
 *
 * The arguments are the ones captured server-side when the model requested the call;
 * they are shown to the user and, on approval, replayed verbatim.
 */
public record PendingActionView(Long id, String toolName, String summary, String expiresAt) {
}
