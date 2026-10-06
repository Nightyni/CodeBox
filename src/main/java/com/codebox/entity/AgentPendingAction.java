package com.codebox.entity;

import java.time.LocalDateTime;

/**
 * A write operation the agent proposed but did not perform.
 *
 * Persisted rather than held in the browser so that approval is authoritative: the
 * arguments captured here are replayed verbatim, which means the model cannot swap in
 * different data between proposing and executing.
 */
public class AgentPendingAction {

    private Long id;
    private Long userId;
    private String toolCallId;
    private String toolName;
    /** Raw JSON arguments exactly as the model produced them. */
    private String arguments;
    /** PENDING / CONFIRMED / CANCELLED / EXPIRED. */
    private String status;
    private LocalDateTime createTime;
    private LocalDateTime expiresAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }

    public String getToolCallId() { return toolCallId; }
    public void setToolCallId(String toolCallId) { this.toolCallId = toolCallId; }

    public String getToolName() { return toolName; }
    public void setToolName(String toolName) { this.toolName = toolName; }

    public String getArguments() { return arguments; }
    public void setArguments(String arguments) { this.arguments = arguments; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }

    public LocalDateTime getExpiresAt() { return expiresAt; }
    public void setExpiresAt(LocalDateTime expiresAt) { this.expiresAt = expiresAt; }
}
