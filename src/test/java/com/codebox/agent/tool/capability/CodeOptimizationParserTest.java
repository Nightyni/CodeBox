package com.codebox.agent.tool.capability;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CodeOptimizationParserTest {

    @Test
    @DisplayName("parses the requested format: change list plus a fenced code block")
    void parsesExpectedFormat() {
        String raw = """
                ### 改动说明
                - 增加分页参数
                - 补充空值校验

                ### 优化后的代码
                ```java
                public List<User> getUsers(int pageNum) {
                    return mapper.selectPage(pageNum);
                }
                ```
                """;

        OptimizedCodeResult r = CodeOptimizationParser.parse(raw);

        assertThat(r.changes()).containsExactly("增加分页参数", "补充空值校验");
        assertThat(r.optimizedCode()).contains("selectPage(pageNum)");
        assertThat(r.optimizedCode()).doesNotContain("```");
        assertThat(r.hasCode()).isTrue();
    }

    @Test
    @DisplayName("tolerates a preamble before the sections")
    void toleratesPreamble() {
        String raw = """
                好的，我来帮你优化这段代码。

                ### 改动说明
                - 去掉重复查询

                ### 优化后的代码
                ```
                SELECT 1;
                ```
                """;

        OptimizedCodeResult r = CodeOptimizationParser.parse(raw);

        assertThat(r.changes()).containsExactly("去掉重复查询");
        assertThat(r.optimizedCode()).isEqualTo("SELECT 1;");
    }

    @Test
    @DisplayName("picks the longest block, not the first illustrative snippet")
    void picksLongestCodeBlock() {
        String raw = """
                ### 改动说明
                - 改进了逻辑

                这是关键一行：
                ```java
                int x = 1;
                ```

                ### 优化后的代码
                ```java
                public void real() {
                    int x = 1;
                    int y = 2;
                    System.out.println(x + y);
                }
                ```
                """;

        OptimizedCodeResult r = CodeOptimizationParser.parse(raw);

        assertThat(r.optimizedCode()).contains("System.out.println");
    }

    @Test
    @DisplayName("handles a language-less fence")
    void handlesLanguageLessFence() {
        OptimizedCodeResult r = CodeOptimizationParser.parse("""
                ### 改动说明
                - 无

                ### 优化后的代码
                ```
                echo hi
                ```
                """);

        assertThat(r.optimizedCode()).isEqualTo("echo hi");
    }

    @Test
    @DisplayName("supports numbered change lists")
    void supportsNumberedList() {
        OptimizedCodeResult r = CodeOptimizationParser.parse("""
                ### 改动说明
                1. 第一条改动
                2. 第二条改动

                ### 优化后的代码
                ```
                code
                ```
                """);

        assertThat(r.changes()).containsExactly("第一条改动", "第二条改动");
    }

    @Test
    @DisplayName("code block without a trailing newline before the fence still parses")
    void codeWithoutTrailingNewline() {
        OptimizedCodeResult r = CodeOptimizationParser.parse("""
                ### 改动说明
                - x

                ### 优化后的代码
                ```
                int a = 1;
                int b = 2;
                ```
                """);

        assertThat(r.optimizedCode()).contains("int a = 1;").contains("int b = 2;");
    }

    @Test
    @DisplayName("no code block: keeps the prose so nothing is lost")
    void noCodeBlockKeepsRawText() {
        OptimizedCodeResult r = CodeOptimizationParser.parse("这段代码已经足够好，无需优化。");

        assertThat(r.hasCode()).isFalse();
        assertThat(r.rawResponse()).contains("无需优化");
    }

    @Test
    @DisplayName("no change section: code still parses, change list is empty")
    void missingChangeSectionStillReturnsCode() {
        OptimizedCodeResult r = CodeOptimizationParser.parse("""
                ```
                SELECT 2;
                ```
                """);

        assertThat(r.changes()).isEmpty();
        assertThat(r.optimizedCode()).isEqualTo("SELECT 2;");
    }

    @Test
    void nullAndBlankAreHandled() {
        assertThat(CodeOptimizationParser.parse(null).hasCode()).isFalse();
        assertThat(CodeOptimizationParser.parse("").hasCode()).isFalse();
        assertThat(CodeOptimizationParser.parse("   ").hasCode()).isFalse();
    }

    @Test
    @DisplayName("a heading terminates the change list")
    void changeListStopsAtNextHeading() {
        OptimizedCodeResult r = CodeOptimizationParser.parse("""
                ### 改动说明
                - 只应有一条

                ### 其他说明
                - 这条不应被当成改动
                ```java
                int a = 1;
                ```
                """);

        assertThat(r.changes()).containsExactly("只应有一条");
    }

    @Test
    void blankLinesInsideChangeListAreSkipped() {
        OptimizationParserFixture fixture = new OptimizationParserFixture();
        assertThat(fixture.clean("-   ")).isEmpty();
        assertThat(fixture.clean("  - a  ")).isEqualTo("a");
    }

    /** Small helper so the package-private cleaner can be exercised directly. */
    static class OptimizationParserFixture {
        String clean(String line) {
            return CodeOptimizationParser.cleanBullet(line);
        }
    }
}
