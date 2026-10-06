package com.codebox.agent.service;

import com.codebox.agent.dto.PendingActionView;
import com.codebox.entity.AgentPendingAction;
import com.codebox.mapper.AgentPendingActionMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

/**
 * Stores and resolves agent write-actions awaiting user approval.
 *
 * Approval is authoritative server-side state, not a client flag: the arguments are
 * persisted when the model proposes the call and replayed from the database on
 * approval, so nothing the model produces during the confirmation turn can change
 * what actually executes.
 */
@Service
public class PendingActionService {

    private static final Logger log = LoggerFactory.getLogger(PendingActionService.class);

    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_CONFIRMED = "CONFIRMED";
    public static final String STATUS_CANCELLED = "CANCELLED";
    public static final String STATUS_EXPIRED = "EXPIRED";

    /** How long an approval stays valid before it must be re-proposed. */
    private static final int TTL_MINUTES = 10;

    private final AgentPendingActionMapper mapper;

    public PendingActionService(AgentPendingActionMapper mapper) {
        this.mapper = mapper;
    }

    public AgentPendingAction create(Long userId, String toolCallId, String toolName, String arguments) {
        AgentPendingAction action = new AgentPendingAction();
        action.setUserId(userId);
        action.setToolCallId(toolCallId);
        action.setToolName(toolName);
        action.setArguments(arguments == null ? "{}" : arguments);
        action.setStatus(STATUS_PENDING);
        action.setCreateTime(LocalDateTime.now());
        action.setExpiresAt(LocalDateTime.now().plusMinutes(TTL_MINUTES));
        mapper.insert(action);
        // Log after the insert: an argument like action.getId() is evaluated eagerly,
        // so logging it in the same statement would always print "null" because
        // useGeneratedKeys has not populated the id yet at that point.
        log.info("Pending action {} created: user={} tool={}",
                action.getId(), userId, toolName);
        return action;
    }

    /** Outcome of validating a confirmation request. */
    public enum Resolution { OK, NOT_FOUND, NOT_OWNER, NOT_PENDING, EXPIRED }

    public record Resolved(Resolution resolution, AgentPendingAction action) {
        public boolean isOk() {
            return resolution == Resolution.OK;
        }

        public String message() {
            return switch (resolution) {
                case OK -> "";
                case NOT_FOUND -> "待确认操作不存在或已被处理";
                case NOT_OWNER -> "无权确认该操作";
                case NOT_PENDING -> "该操作已被处理，请勿重复确认";
                case EXPIRED -> "该操作已过期，请重新发起请求";
            };
        }
    }

    /**
     * Validates a confirmation and atomically claims the action.
     *
     * @param userId id of the authenticated user - checked against the stored owner so
     *               one user can never approve another user's action
     */
    public Resolved claim(Long pendingActionId, Long userId) {
        AgentPendingAction action = mapper.findById(pendingActionId);
        if (action == null) {
            return new Resolved(Resolution.NOT_FOUND, null);
        }
        if (!action.getUserId().equals(userId)) {
            log.warn("User {} attempted to confirm pending action {} owned by {}",
                    userId, pendingActionId, action.getUserId());
            return new Resolved(Resolution.NOT_OWNER, null);
        }
        if (!STATUS_PENDING.equals(action.getStatus())) {
            return new Resolved(Resolution.NOT_PENDING, null);
        }
        if (action.getExpiresAt() != null && action.getExpiresAt().isBefore(LocalDateTime.now())) {
            mapper.updateStatus(action.getId(), STATUS_EXPIRED, STATUS_PENDING);
            return new Resolved(Resolution.EXPIRED, null);
        }

        // Conditional update: only one concurrent confirmation can win this race.
        int claimed = mapper.updateStatus(action.getId(), STATUS_CONFIRMED, STATUS_PENDING);
        if (claimed != 1) {
            return new Resolved(Resolution.NOT_PENDING, null);
        }
        return new Resolved(Resolution.OK, action);
    }

    public void cancel(Long pendingActionId) {
        mapper.updateStatus(pendingActionId, STATUS_CANCELLED, STATUS_PENDING);
    }

    /** View model for the client, deliberately excluding the raw arguments. */
    public PendingActionView toView(AgentPendingAction action, String summary) {
        return new PendingActionView(
                action.getId(),
                action.getToolName(),
                summary,
                action.getArguments(),
                action.getExpiresAt() == null ? null : action.getExpiresAt().toString());
    }
}
