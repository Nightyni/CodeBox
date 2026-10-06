package com.codebox.agent.tool.capability;

import com.codebox.agent.tool.capability.DiffEngine.DiffLine;
import com.codebox.agent.tool.capability.DiffEngine.DiffResult;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class DiffEngineTest {

    private static List<String> types(DiffResult r) {
        return r.lines().stream().map(DiffLine::type).toList();
    }

    @Test
    @DisplayName("identical code produces no changes")
    void identicalCodeHasNoChanges() {
        DiffResult r = DiffEngine.diff("a\nb\nc", "a\nb\nc");

        assertThat(r.added()).isZero();
        assertThat(r.removed()).isZero();
        assertThat(types(r)).containsOnly("EQUAL");
    }

    @Test
    @DisplayName("an added line is marked ADDED and only appears on the right")
    void detectsAddedLine() {
        DiffResult r = DiffEngine.diff("a\nc", "a\nb\nc");

        assertThat(r.added()).isEqualTo(1);
        assertThat(r.removed()).isZero();

        DiffLine added = r.lines().stream().filter(l -> "ADDED".equals(l.type())).findFirst().orElseThrow();
        assertThat(added.optimized()).isEqualTo("b");
        assertThat(added.original()).isNull();
        assertThat(added.originalNumber()).isNull();
        assertThat(added.optimizedNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("a removed line is marked REMOVED and only appears on the left")
    void detectsRemovedLine() {
        DiffResult r = DiffEngine.diff("a\nb\nc", "a\nc");

        assertThat(r.removed()).isEqualTo(1);
        assertThat(r.added()).isZero();

        DiffLine removed = r.lines().stream().filter(l -> "REMOVED".equals(l.type())).findFirst().orElseThrow();
        assertThat(removed.original()).isEqualTo("b");
        assertThat(removed.optimized()).isNull();
    }

    @Test
    @DisplayName("a changed line shows as one removal plus one addition")
    void detectsReplacement() {
        DiffResult r = DiffEngine.diff("old line", "new line");

        assertThat(r.added()).isEqualTo(1);
        assertThat(r.removed()).isEqualTo(1);
        assertThat(types(r)).containsExactlyInAnyOrder("REMOVED", "ADDED");
    }

    @Test
    @DisplayName("line numbers advance independently on each side")
    void lineNumbersAreTrackedPerSide() {
        // original: 1 a, 2 b, 3 c    optimized: 1 a, 2 c
        DiffResult r = DiffEngine.diff("a\nb\nc", "a\nc");

        DiffLine equalFirst = r.lines().get(0);
        assertThat(equalFirst.originalNumber()).isEqualTo(1);
        assertThat(equalFirst.optimizedNumber()).isEqualTo(1);

        DiffLine last = r.lines().get(r.lines().size() - 1);
        assertThat(last.type()).isEqualTo("EQUAL");
        assertThat(last.originalNumber()).isEqualTo(3);
        assertThat(last.optimizedNumber()).isEqualTo(2);
    }

    @Test
    @DisplayName("CRLF and LF are treated as the same line ending")
    void lineEndingsAreNormalised() {
        DiffResult r = DiffEngine.diff("a\r\nb\r\n", "a\nb\n");

        assertThat(r.added()).isZero();
        assertThat(r.removed()).isZero();
    }

    @Test
    void emptyOriginalMeansEverythingAdded() {
        DiffResult r = DiffEngine.diff("", "a\nb");

        assertThat(r.added()).isEqualTo(2);
        assertThat(r.removed()).isZero();
    }

    @Test
    void emptyOptimizedMeansEverythingRemoved() {
        DiffResult r = DiffEngine.diff("a\nb", "");

        assertThat(r.removed()).isEqualTo(2);
        assertThat(r.added()).isZero();
    }

    @Test
    void bothEmptyProducesNothing() {
        DiffResult r = DiffEngine.diff("", "");

        assertThat(r.lines()).isEmpty();
        assertThat(r.added()).isZero();
        assertThat(r.removed()).isZero();
    }

    @Test
    void nullInputsAreTreatedAsEmpty() {
        assertThat(DiffEngine.diff(null, null).lines()).isEmpty();
        assertThat(DiffEngine.diff(null, "x").added()).isEqualTo(1);
    }

    @Test
    @DisplayName("a trailing newline does not create a phantom empty line")
    void trailingNewlineIsNotALine() {
        assertThat(DiffEngine.splitLines("a\nb\n")).containsExactly("a", "b");
        assertThat(DiffEngine.splitLines("a\nb")).containsExactly("a", "b");
        // an intentional blank line in the middle must survive
        assertThat(DiffEngine.splitLines("a\n\nb")).containsExactly("a", "", "b");
    }

    @Test
    @DisplayName("indentation-only changes are reported (whitespace is not normalised away)")
    void indentationChangesAreDetected() {
        DiffResult r = DiffEngine.diff("if (x) {\ny();\n}", "if (x) {\n    y();\n}");

        assertThat(r.added()).isEqualTo(1);
        assertThat(r.removed()).isEqualTo(1);
    }

    @Test
    @DisplayName("a realistic rewrite produces a sane diff")
    void realisticRewrite() {
        String original = """
                public List<User> getUsers() {
                    return userMapper.selectAll();
                }""";
        String optimized = """
                public List<User> getUsers(int pageNum, int pageSize) {
                    int offset = (pageNum - 1) * pageSize;
                    return userMapper.selectPage(offset, pageSize);
                }""";

        DiffResult r = DiffEngine.diff(original, optimized);

        assertThat(r.added()).isGreaterThan(0);
        assertThat(r.removed()).isGreaterThan(0);
        // the closing brace is common to both, so it must be preserved as context
        assertThat(types(r)).contains("EQUAL");
    }

    @Test
    @DisplayName("very large inputs are refused instead of emitting a bogus diff")
    void oversizedInputIsRefused() {
        String huge = String.join("\n", java.util.Collections.nCopies(5000, "x"));

        DiffResult r = DiffEngine.diff(huge, huge + "\ny");

        assertThat(r.lines()).isEmpty();
        assertThat(r.added()).isZero();
    }
}
