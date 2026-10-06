package com.codebox.agent.dto;

/**
 * Outcome of running a tool.
 *
 * Failures are returned as normal results rather than thrown: the model must see
 * the error text so it can recover (retry with different arguments, or explain the
 * problem to the user). Throwing would abort the whole turn.
 */
public record ToolResult(boolean success, String text) {

    public static ToolResult ok(String text) {
        return new ToolResult(true, text);
    }

    public static ToolResult error(String text) {
        return new ToolResult(false, text);
    }
}
