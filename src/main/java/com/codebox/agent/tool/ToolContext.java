package com.codebox.agent.tool;

/**
 * Per-invocation context.
 *
 * The user id lives here and ONLY here: it is injected server-side from the HTTP
 * session and is deliberately never a tool parameter, so the model has no way to
 * address another user's data no matter what it puts in the arguments.
 */
public record ToolContext(Long userId) {
}
