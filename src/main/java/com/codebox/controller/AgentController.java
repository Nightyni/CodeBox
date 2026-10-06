package com.codebox.controller;

import com.codebox.agent.dto.AgentResponse;
import com.codebox.agent.dto.ChatMessage;
import com.codebox.agent.dto.ChatRequest;
import com.codebox.agent.dto.ConfirmRequest;
import com.codebox.agent.service.PendingActionService;
import com.codebox.agent.service.SnippetAgent;
import com.codebox.entity.AgentPendingAction;
import com.codebox.entity.User;
import com.codebox.service.AuthService;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * The AI assistant entry point.
 *
 * Distinct from /api/ask: that endpoint answers questions from the knowledge base
 * (retrieval only), while this one can act on the business system through tools.
 */
@RestController
@RequestMapping("/api/agent")
public class AgentController extends BaseController {

    private final SnippetAgent snippetAgent;
    private final PendingActionService pendingActionService;

    public AgentController(AuthService authService,
                           SnippetAgent snippetAgent,
                           PendingActionService pendingActionService) {
        super(authService);
        this.snippetAgent = snippetAgent;
        this.pendingActionService = pendingActionService;
    }

    @PostMapping("/chat")
    public AgentResponse chat(HttpSession session, @Valid @RequestBody ChatRequest request) {
        User user = currentUser(session);
        List<ChatMessage> history = request.getHistory();
        return snippetAgent.chat(user.getId(), request.getMessage().strip(), history);
    }

    /**
     * Approves or rejects a write action the agent proposed.
     *
     * The stored arguments are replayed, so approval cannot be redirected to different
     * data than what the user was shown.
     */
    @PostMapping("/confirm")
    public AgentResponse confirm(HttpSession session, @Valid @RequestBody ConfirmRequest request) {
        User user = currentUser(session);

        PendingActionService.Resolved resolved =
                pendingActionService.claim(request.getPendingActionId(), user.getId());
        if (!resolved.isOk()) {
            throw new IllegalArgumentException(resolved.message());
        }

        AgentPendingAction action = resolved.action();
        if (!request.isApproved()) {
            pendingActionService.cancel(action.getId());
            return new AgentResponse("已取消该操作，未对数据做任何修改。",
                    List.of(), null, List.of(), List.of(), 0, 0, 0);
        }
        return snippetAgent.executeApproved(user.getId(), action);
    }
}
