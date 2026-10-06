package com.codebox.agent.tool.capability;

import java.util.List;

/**
 * Structured result of an AI code optimisation.
 *
 * Deliberately not a free-text blob: the previous version returned the model's raw
 * markdown (a "changes" list plus a fenced code block), which the UI could only print
 * verbatim. Splitting it into fields is what makes a real side-by-side diff possible,
 * and it keeps the change list and the code guaranteed to belong together.
 *
 * @param changes       what the model says it changed, one item per change
 * @param optimizedCode the improved code, extracted from the fenced block
 * @param rawResponse   the model's original reply, kept for debugging and for the
 *                      fallback path when the expected format is not followed
 */
public record OptimizedCodeResult(List<String> changes, String optimizedCode, String rawResponse) {

    public boolean hasChanges() {
        return changes != null && !changes.isEmpty();
    }

    public boolean hasCode() {
        return optimizedCode != null && !optimizedCode.isBlank();
    }
}
