package com.codebox.dto;

import java.util.List;

/**
 * Answer grounded in retrieved snippets.
 *
 * {@code insufficientEvidence} = the context genuinely did not support an answer.
 * {@code llmFailed}            = the model call itself failed (bad key, timeout, rate limit).
 * These are deliberately separate: one is a knowledge-base gap, the other is an outage.
 */
public record AskResponse(
        String question,
        String answer,
        List<Citation> citations,
        boolean insufficientEvidence,
        boolean llmFailed,
        long elapsedMs
) {
    public record Citation(Long snippetId, String title, String language, double score) {}
}