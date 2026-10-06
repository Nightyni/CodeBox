package com.codebox.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.util.List;

/**
 * Request body for /api/agent/chat.
 *
 * History is supplied by the client for now so the agent can hold a multi-turn
 * conversation before session persistence exists.
 */
public class ChatRequest {

    @NotBlank(message = "消息不能为空")
    @Size(max = 2000, message = "消息过长")
    private String message;

    /** Prior turns, oldest first. May be empty. */
    @Size(max = 40, message = "对话历史过长")
    private List<ChatMessage> history;

    public String getMessage() { return message; }
    public void setMessage(String message) { this.message = message; }

    public List<ChatMessage> getHistory() { return history; }
    public void setHistory(List<ChatMessage> history) { this.history = history; }
}
