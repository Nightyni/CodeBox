package com.codebox.agent;

import com.codebox.agent.service.PendingActionService;
import com.codebox.entity.AgentPendingAction;
import com.codebox.mapper.AgentPendingActionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The approval gate is a security boundary, so it is tested directly:
 * ownership, expiry and single-execution are the properties that matter.
 */
class PendingActionServiceTest {

    private AgentPendingActionMapper mapper;
    private PendingActionService service;

    @BeforeEach
    void setUp() {
        mapper = mock(AgentPendingActionMapper.class);
        service = new PendingActionService(mapper);
    }

    private AgentPendingAction action(Long userId, String status, LocalDateTime expiresAt) {
        AgentPendingAction a = new AgentPendingAction();
        a.setId(1L);
        a.setUserId(userId);
        a.setToolName("delete_snippet");
        a.setArguments("{\"id\":5}");
        a.setStatus(status);
        a.setExpiresAt(expiresAt);
        return a;
    }

    @Test
    @DisplayName("creation stores the action as PENDING with a future expiry")
    void createStoresPending() {
        service.create(7L, "call_1", "create_snippet", "{\"title\":\"x\"}");

        verify(mapper).insert(any(AgentPendingAction.class));
    }

    @Test
    void ownerCanClaim() {
        when(mapper.findById(1L)).thenReturn(action(7L, "PENDING", LocalDateTime.now().plusMinutes(5)));
        when(mapper.updateStatus(eq(1L), eq("CONFIRMED"), eq("PENDING"))).thenReturn(1);

        var resolved = service.claim(1L, 7L);

        assertThat(resolved.isOk()).isTrue();
        assertThat(resolved.action().getToolName()).isEqualTo("delete_snippet");
    }

    @Test
    @DisplayName("a different user cannot confirm someone else's action")
    void nonOwnerIsRejected() {
        when(mapper.findById(1L)).thenReturn(action(7L, "PENDING", LocalDateTime.now().plusMinutes(5)));

        var resolved = service.claim(1L, 99L);

        assertThat(resolved.resolution()).isEqualTo(PendingActionService.Resolution.NOT_OWNER);
        assertThat(resolved.action()).isNull();
        // crucially, the status must NOT have been touched
        verify(mapper, never()).updateStatus(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("an already-handled action cannot be replayed")
    void alreadyHandledIsRejected() {
        when(mapper.findById(1L)).thenReturn(action(7L, "CONFIRMED", LocalDateTime.now().plusMinutes(5)));

        var resolved = service.claim(1L, 7L);

        assertThat(resolved.resolution()).isEqualTo(PendingActionService.Resolution.NOT_PENDING);
        verify(mapper, never()).updateStatus(anyLong(), anyString(), anyString());
    }

    @Test
    @DisplayName("an expired action is rejected and marked EXPIRED")
    void expiredIsRejected() {
        when(mapper.findById(1L)).thenReturn(action(7L, "PENDING", LocalDateTime.now().minusMinutes(1)));

        var resolved = service.claim(1L, 7L);

        assertThat(resolved.resolution()).isEqualTo(PendingActionService.Resolution.EXPIRED);
        verify(mapper).updateStatus(1L, "EXPIRED", "PENDING");
    }

    @Test
    void unknownActionIsReported() {
        when(mapper.findById(404L)).thenReturn(null);

        assertThat(service.claim(404L, 7L).resolution())
                .isEqualTo(PendingActionService.Resolution.NOT_FOUND);
    }

    @Test
    @DisplayName("losing the conditional update race reports NOT_PENDING, not success")
    void raceLosesCleanly() {
        when(mapper.findById(1L)).thenReturn(action(7L, "PENDING", LocalDateTime.now().plusMinutes(5)));
        // another request already claimed it between our read and our write
        when(mapper.updateStatus(eq(1L), eq("CONFIRMED"), eq("PENDING"))).thenReturn(0);

        var resolved = service.claim(1L, 7L);

        assertThat(resolved.isOk()).isFalse();
        assertThat(resolved.resolution()).isEqualTo(PendingActionService.Resolution.NOT_PENDING);
    }

    @Test
    void cancelOnlyTransitionsFromPending() {
        service.cancel(1L);

        verify(mapper).updateStatus(1L, "CANCELLED", "PENDING");
    }
}
