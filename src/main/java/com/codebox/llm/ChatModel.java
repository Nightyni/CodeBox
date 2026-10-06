package com.codebox.llm;

import com.codebox.agent.dto.AssistantTurn;
import com.codebox.agent.dto.ChatMessage;
import com.codebox.agent.dto.ToolSpec;

import java.util.List;

/**
 * Minimal abstraction over a chat model so the RAG pipeline can be unit-tested
 * with a fake implementation and never needs a live API key in tests.
 */
public interface ChatModel {

    String complete(String systemPrompt, String userPrompt);

    boolean available();

    /**
     * True when the most recent {@link #complete} call failed (timeout, bad key,
     * rate limit, network) rather than the model answering normally.
     *
     * Callers must not report a failed call to the user as "not enough evidence":
     * one is an infrastructure problem, the other is a knowledge-base gap.
     */
    default boolean lastCallFailed() {
        return false;
    }

    /**
     * Whether this model can call tools.
     *
     * Defaults to false so existing single-shot callers keep working unchanged; the
     * agent checks this before starting a tool loop rather than failing mid-run.
     */
    default boolean supportsTools() {
        return false;
    }

    /**
     * Multi-turn call that exposes {@code tools} to the model.
     *
     * Unlike {@link #complete}, the reply may be a request to run tools instead of
     * prose, which is what makes an agent loop possible.
     */
    default AssistantTurn completeWithTools(List<ChatMessage> messages, List<ToolSpec> tools) {
        throw new UnsupportedOperationException("This model does not support tool calling");
    }
}
