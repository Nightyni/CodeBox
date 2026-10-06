package com.codebox.agent.dto;

import jakarta.validation.constraints.NotNull;

/** Request body for /api/agent/confirm. */
public class ConfirmRequest {

    @NotNull(message = "pendingActionId 不能为空")
    private Long pendingActionId;

    /** true to execute the action, false to discard it. */
    private boolean approved;

    public Long getPendingActionId() { return pendingActionId; }
    public void setPendingActionId(Long pendingActionId) { this.pendingActionId = pendingActionId; }

    public boolean isApproved() { return approved; }
    public void setApproved(boolean approved) { this.approved = approved; }
}
