package com.codebox.agent.tool.capability;

import com.codebox.agent.dto.ToolResult;
import com.codebox.agent.tool.AgentToolTestSupport;
import com.codebox.agent.tool.RiskLevel;
import com.codebox.llm.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Covers both AI capability tools.
 *
 * They share the same failure modes (missing id, not owned, model outage) and the same
 * safety property (READ-only, never write), so they are tested together.
 */
class CapabilityToolsTest extends AgentToolTestSupport {

    private static final String GOOD_ANALYSIS = """
            功能：分页查询用户。
            核心逻辑：
            1. 计算 offset
            潜在问题：
            - 没有排序，分页不稳定
            优化建议：
            - 增加 ORDER BY id
            """;

    private static final String GOOD_OPTIMIZATION = """
            ### 改动说明
            - 增加 ORDER BY
            - 补充总数查询

            ### 优化后的代码
            ```sql
            SELECT id FROM users ORDER BY id LIMIT 10;
            ```
            """;

    private AnalyzeSnippetTool analyzeTool(ChatModel model) {
        return new AnalyzeSnippetTool(new CodeAnalyzer(model, snippetService), json);
    }

    private OptimizeSnippetTool optimizeTool(ChatModel model) {
        return new OptimizeSnippetTool(new CodeAnalyzer(model, snippetService), json);
    }

    // ------------------------------------------------------------ analyze

    @Test
    void analyzeIsAReadTool() {
        assertThat(analyzeTool(scriptedModel(GOOD_ANALYSIS)).risk()).isEqualTo(RiskLevel.READ);
        assertThat(analyzeTool(scriptedModel(GOOD_ANALYSIS)).spec().name()).isEqualTo("analyze_snippet");
    }

    @Test
    void analyzeRejectsMissingId() {
        ToolResult r = fail("{}", analyzeTool(scriptedModel(GOOD_ANALYSIS)));

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("id");
    }

    @Test
    void analyzeRejectsNonNumericId() {
        assertThat(fail("{\"id\":\"abc\"}", analyzeTool(scriptedModel(GOOD_ANALYSIS))).success()).isFalse();
    }

    @Test
    void analyzeRejectsMalformedJson() {
        assertThat(fail("oops", analyzeTool(scriptedModel(GOOD_ANALYSIS))).success()).isFalse();
    }

    @Test
    @DisplayName("analyze refuses a snippet owned by someone else")
    void analyzeRejectsNotOwned() {
        givenNotOwned();

        ToolResult r = analyzeTool(scriptedModel(GOOD_ANALYSIS)).execute("{\"id\":3}", CONTEXT);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("找不到");
    }

    @Test
    void analyzeReturnsTheAnalysis() {
        givenOwned(snippet(3L, "分页查询", "SELECT 1", "SQL"));

        ToolResult r = analyzeTool(scriptedModel(GOOD_ANALYSIS)).execute("{\"id\":3}", CONTEXT);

        assertThat(r.success()).isTrue();
        var node = parse(r);
        assertThat(node.get("snippetId").asLong()).isEqualTo(3L);
        assertThat(node.get("title").asText()).isEqualTo("分页查询");
        assertThat(node.get("analysis").asText()).contains("潜在问题");
    }

    @Test
    @DisplayName("a model outage is reported as a failure, never as a fabricated analysis")
    void analyzeDoesNotFabricateOnModelFailure() {
        givenOwned(snippet(3L, "t", "c", "Java"));

        ToolResult r = analyzeTool(scriptedModel("")).execute("{\"id\":3}", CONTEXT);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("失败");
        assertThat(r.text()).contains("不要编造");
    }

    // ------------------------------------------------------------ optimize

    @Test
    void optimizeIsAReadToolBecauseItWritesNothing() {
        assertThat(optimizeTool(scriptedModel(GOOD_OPTIMIZATION)).risk()).isEqualTo(RiskLevel.READ);
    }

    @Test
    void optimizeRejectsMissingId() {
        assertThat(fail("{}", optimizeTool(scriptedModel(GOOD_OPTIMIZATION))).success()).isFalse();
    }

    @Test
    void optimizeRejectsNotOwned() {
        givenNotOwned();

        ToolResult r = optimizeTool(scriptedModel(GOOD_OPTIMIZATION)).execute("{\"id\":3}", CONTEXT);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("找不到");
    }

    @Test
    @DisplayName("optimize returns structured changes, the code, and a computed diff")
    void optimizeReturnsStructuredResultWithDiff() {
        givenOwned(snippet(3L, "分页查询", "SELECT id FROM users LIMIT 10;", "SQL"));

        ToolResult r = optimizeTool(scriptedModel(GOOD_OPTIMIZATION)).execute("{\"id\":3}", CONTEXT);

        assertThat(r.success()).isTrue();
        var node = parse(r);

        assertThat(node.get("changes")).hasSize(2);
        assertThat(node.get("changes").get(0).asText()).contains("ORDER BY");
        // the fenced block was extracted, not left as raw markdown
        assertThat(node.get("optimizedCode").asText()).contains("LIMIT 10").doesNotContain("```");
        // a diff was computed against the original
        assertThat(node.get("diff").isArray()).isTrue();
        assertThat(node.get("diff").size()).isGreaterThan(0);
        assertThat(node.get("diffSummary").get("added").asInt()).isGreaterThanOrEqualTo(0);
        assertThat(node.get("note").asText()).contains("create_snippet");
    }

    @Test
    @DisplayName("optimize never writes: the user must confirm before anything is saved")
    void optimizePerformsNoWrites() {
        givenOwned(snippet(3L, "t", "SELECT 1", "SQL"));

        optimizeTool(scriptedModel(GOOD_OPTIMIZATION)).execute("{\"id\":3}", CONTEXT);

        verify(snippetMapper, never()).insert(any());
        verify(snippetMapper, never()).update(any());
        verify(snippetMapper, never()).delete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("an unparseable reply is a failure rather than a bogus empty optimisation")
    void optimizeFailsWhenNoCodeBlockIsReturned() {
        givenOwned(snippet(3L, "t", "SELECT 1", "SQL"));

        ToolResult r = optimizeTool(scriptedModel("这段代码已经足够好，无需优化。"))
                .execute("{\"id\":3}", CONTEXT);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("不要编造");
    }

    @Test
    void optimizeFailsOnModelOutage() {
        givenOwned(snippet(3L, "t", "SELECT 1", "SQL"));

        ToolResult r = optimizeTool(scriptedModel("")).execute("{\"id\":3}", CONTEXT);

        assertThat(r.success()).isFalse();
    }

    @Test
    @DisplayName("the diff compares against the stored original, not something else")
    void diffUsesTheStoredOriginal() {
        givenOwned(snippet(3L, "t", "ORIGINAL_LINE;", "SQL"));
        String reply = """
                ### 改动说明
                - 全量替换

                ### 优化后的代码
                ```sql
                REPLACED_LINE;
                ```
                """;

        var node = parse(optimizeTool(scriptedModel(reply)).execute("{\"id\":3}", CONTEXT));

        assertThat(node.get("diffSummary").get("added").asInt()).isEqualTo(1);
        assertThat(node.get("diffSummary").get("removed").asInt()).isEqualTo(1);
        String diff = node.get("diff").toString();
        assertThat(diff).contains("ORIGINAL_LINE").contains("REPLACED_LINE");
    }

    @Test
    @DisplayName("the optional focus is forwarded to the model prompt")
    void focusIsForwarded() {
        givenOwned(snippet(3L, "t", "SELECT 1", "SQL"));
        var models = new java.util.ArrayList<String>();
        ChatModel recording = new ChatModel() {
            @Override
            public String complete(String systemPrompt, String userPrompt) {
                models.add(userPrompt);
                return GOOD_OPTIMIZATION;
            }

            @Override
            public boolean available() {
                return true;
            }
        };

        optimizeTool(recording).execute("{\"id\":3,\"focus\":\"性能\"}", CONTEXT);

        assertThat(models).hasSize(1);
        assertThat(models.get(0)).contains("性能");
    }
}
