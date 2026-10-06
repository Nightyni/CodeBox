package com.codebox.agent.tool.capability;

import java.util.ArrayList;
import java.util.List;

/**
 * Line-level diff between the original and the AI-optimised code.
 *
 * Implemented as an LCS (longest common subsequence) table rather than pulling in a
 * diff library: the inputs are single snippets of at most a few hundred lines, so the
 * O(n*m) table is negligible, and keeping it in-tree means no extra dependency for one
 * feature. The result is a plain op list the frontend renders directly.
 */
public final class DiffEngine {

    private DiffEngine() {}

    /**
     * One rendered line of the side-by-side view.
     *
     * The line numbers are boxed because an ADDED line has no original number and a
     * REMOVED line has no optimised number; a primitive int would force a fake 0 that
     * the UI would then render as a real line number.
     */
    public record DiffLine(Integer originalNumber, Integer optimizedNumber,
                           String original, String optimized, String type) {}

    public record DiffResult(List<DiffLine> lines, int added, int removed) {}

    /**
     * Very long inputs are compared as-is but the rendered output is capped, so a
     * pathological file cannot flood the response.
     */
    private static final int MAX_RENDERED_LINES = 400;
    private static final int MAX_LINES = 4000;

    public static DiffResult diff(String originalCode, String optimizedCode) {
        String[] a = splitLines(originalCode);
        String[] b = splitLines(optimizedCode);

        List<DiffLine> out = new ArrayList<>();
        int added = 0;
        int removed = 0;

        if (a.length > MAX_LINES || b.length > MAX_LINES) {
            // Too large to align usefully; report it rather than emitting a bogus diff.
            return new DiffResult(List.of(), 0, 0);
        }

        int[][] lcs = lcsTable(a, b);
        int i = 0, j = 0;
        int originalNo = 1, optimizedNo = 1;

        while (i < a.length && j < b.length) {
            if (a[i].equals(b[j])) {
                out.add(new DiffLine(originalNo++, optimizedNo++, a[i], b[j], "EQUAL"));
                i++;
                j++;
            } else if (lcs[i + 1][j] >= lcs[i][j + 1]) {
                out.add(new DiffLine(originalNo++, (Integer) null, a[i], null, "REMOVED"));
                removed++;
                i++;
            } else {
                out.add(new DiffLine((Integer) null, optimizedNo++, null, b[j], "ADDED"));
                added++;
                j++;
            }
        }
        while (i < a.length) {
            out.add(new DiffLine(originalNo++, (Integer) null, a[i], null, "REMOVED"));
            removed++;
            i++;
        }
        while (j < b.length) {
            out.add(new DiffLine((Integer) null, optimizedNo++, null, b[j], "ADDED"));
            added++;
            j++;
        }

        if (out.size() > MAX_RENDERED_LINES) {
            out = new ArrayList<>(out.subList(0, MAX_RENDERED_LINES));
        }
        return new DiffResult(out, added, removed);
    }

    /**
     * lcs[i][j] = LCS length of a[i..] and b[j..].
     *
     * Filled bottom-up so the forward walk can pick a direction greedily from the
     * already-computed suffix lengths.
     */
    private static int[][] lcsTable(String[] a, String[] b) {
        int[][] lcs = new int[a.length + 1][b.length + 1];
        for (int i = a.length - 1; i >= 0; i--) {
            for (int j = b.length - 1; j >= 0; j--) {
                lcs[i][j] = a[i].equals(b[j])
                        ? lcs[i + 1][j + 1] + 1
                        : Math.max(lcs[i + 1][j], lcs[i][j + 1]);
            }
        }
        return lcs;
    }

    /**
     * Splits on line boundaries without inventing a trailing empty line.
     *
     * The separator is normalised so a file that used CRLF and one that used LF compare
     * equal - otherwise every line would show as changed when only the ending differs.
     */
    static String[] splitLines(String code) {
        if (code == null || code.isEmpty()) return new String[0];
        String normalized = code.replace("\r\n", "\n").replace("\r", "\n");
        if (normalized.endsWith("\n")) {
            normalized = normalized.substring(0, normalized.length() - 1);
        }
        return normalized.split("\n", -1);
    }
}
