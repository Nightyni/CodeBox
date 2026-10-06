package com.codebox.agent.tool.capability;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns the model's optimisation reply into a structured result.
 *
 * The prompt asks for a fixed shape, but a model is not a compiler: it may reorder the
 * sections, wrap the code in a differently-tagged fence, or add a preamble. So parsing
 * is deliberately tolerant and always falls back to the raw text rather than losing the
 * model's answer.
 */
public final class CodeOptimizationParser {

    private CodeOptimizationParser() {}

    /** Matches a fenced block: ```lang\n ... \n``` (also handles ``` alone). */
    private static final Pattern FENCE =
            Pattern.compile("```[ \\t]*([A-Za-z0-9+#._-]*)[ \\t]*\\r?\\n([\\s\\S]*?)```");

    private static final List<String> CHANGE_HEADINGS =
            List.of("改动说明", "修改说明", "变更说明", "改动", "修改", "changes", "change summary");

    public static OptimizedCodeResult parse(String rawResponse) {
        String raw = rawResponse == null ? "" : rawResponse;

        String code = extractLongestFence(raw);
        List<String> changes = extractChanges(stripFences(raw));

        if (code == null) {
            // No fenced block: treat the whole reply as prose and keep it intact so the
            // caller can still show something useful instead of an empty panel.
            return new OptimizedCodeResult(changes, "", raw.trim());
        }
        return new OptimizedCodeResult(changes, code, raw.trim());
    }

    /**
     * Picks the longest fenced block rather than the first.
     *
     * Models often show a short illustrative snippet before the real answer; the full
     * replacement is virtually always the largest block.
     */
    static String extractLongestFence(String raw) {
        Matcher matcher = FENCE.matcher(raw);
        String best = null;
        while (matcher.find()) {
            String body = matcher.group(2);
            if (body == null) continue;
            if (best == null || body.length() > best.length()) best = body;
        }
        return best == null ? null : trimTrailingNewlines(best);
    }

    static List<String> extractChanges(String text) {
        List<String> out = new ArrayList<>();
        if (text == null || text.isBlank()) return out;

        int headingIndex = indexOfAnyHeading(text);
        if (headingIndex < 0) return out;

        String after = text.substring(headingIndex);
        int firstNewline = after.indexOf('\n');
        if (firstNewline < 0) return out;
        after = after.substring(firstNewline + 1);

        for (String line : after.split("\r?\n")) {
            String item = cleanBullet(line);
            if (item.isEmpty()) continue;
            // A new "### ..." section means the change list is over.
            if (line.stripLeading().startsWith("#")) break;
            out.add(item);
            if (out.size() >= 20) break;
        }
        return out;
    }

    private static int indexOfAnyHeading(String text) {
        String lower = text.toLowerCase();
        int best = -1;
        for (String heading : CHANGE_HEADINGS) {
            int found = lower.indexOf(heading.toLowerCase());
            if (found >= 0 && (best < 0 || found < best)) best = found;
        }
        return best;
    }

    /** Strips "-", "*", "1." and "1)" prefixes so the UI can render its own list. */
    static String cleanBullet(String line) {
        String s = line.strip();
        if (s.isEmpty()) return "";
        s = s.replaceFirst("^[-*•]\\s*", "");
        s = s.replaceFirst("^\\d+[.)]\\s*", "");
        return s.strip();
    }

    private static String stripFences(String raw) {
        return FENCE.matcher(raw).replaceAll("");
    }

    private static String trimTrailingNewlines(String s) {
        int end = s.length();
        while (end > 0 && (s.charAt(end - 1) == '\n' || s.charAt(end - 1) == '\r')) end--;
        return s.substring(0, end);
    }
}
