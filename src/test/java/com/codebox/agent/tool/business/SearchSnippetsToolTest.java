package com.codebox.agent.tool.business;

import com.codebox.agent.dto.ToolResult;
import com.codebox.agent.tool.AgentTool;
import com.codebox.agent.tool.AgentToolTestSupport;
import com.codebox.agent.tool.RiskLevel;
import com.codebox.entity.Snippet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SearchSnippetsToolTest extends AgentToolTestSupport {

    private SearchSnippetsTool tool;

    @BeforeEach
    void setUp() {
        tool = new SearchSnippetsTool(snippetService, retrievalService, json);
    }

    @Test
    void searchIsAReadTool() {
        assertThat(tool.risk()).isEqualTo(RiskLevel.READ);
        assertThat(tool.spec().name()).isEqualTo("search_snippets");
    }

    @Test
    void schemaRequiresQuery() {
        assertThat(tool.spec().parameters().toString()).contains("query");
        assertThat(tool.spec().parameters().toString()).contains("required=[query]");
    }

    @Test
    void blankQueryIsRejected() {
        ToolResult result = fail("{\"query\":\"   \"}", tool);

        assertThat(result.success()).isFalse();
        assertThat(result.text()).contains("query");
    }

    @Test
    void malformedJsonIsRejected() {
        ToolResult result = fail("{not json", tool);

        assertThat(result.success()).isFalse();
        assertThat(result.text()).contains("JSON");
    }

    @Test
    void missingQueryIsRejected() {
        ToolResult result = fail("{}", tool);

        assertThat(result.success()).isFalse();
        assertThat(result.text()).contains("query");
    }

    @Test
    @DisplayName("keyword channel finds a snippet even when the vector score is low")
    void findsByKeywordWhenSemanticMisses() {
        Snippet s = snippet(4L, "MyBatis 动态条件查询", "AND title LIKE ...", "XML");
        givenAllSnippets(s);

        ToolResult result = tool.execute("{\"query\":\"MyBatis\"}", CONTEXT);

        assertThat(result.success()).isTrue();
        var node = parse(result);
        assertThat(node.get("count").asInt()).isEqualTo(1);
        assertThat(node.get("results").get(0).get("id").asLong()).isEqualTo(4L);
    }

    @Test
    void noMatchReturnsZeroCountNotAnError() {
        givenAllSnippets();

        ToolResult result = tool.execute("{\"query\":\"完全不存在的关键词zzz\"}", CONTEXT);

        assertThat(result.success()).isTrue();
        assertThat(parse(result).get("count").asInt()).isZero();
    }

    @Test
    @DisplayName("language filter excludes non-matching snippets")
    void languageFilterApplies() {
        givenAllSnippets(
                snippet(1L, "Redis 锁", "redis code", "Java"),
                snippet(2L, "分页 SQL", "sql code", "SQL"));

        ToolResult result = tool.execute("{\"query\":\"code\",\"language\":\"SQL\"}", CONTEXT);

        var results = parse(result).get("results");
        assertThat(results).hasSize(1);
        assertThat(results.get(0).get("language").asText()).isEqualTo("SQL");
    }

    @Test
    @DisplayName("limit is clamped to 1..20 and defaults sensibly")
    void limitIsClamped() {
        givenAllSnippets(
                snippet(1L, "a code", "code", "Java"),
                snippet(2L, "b code", "code", "Java"),
                snippet(3L, "c code", "code", "Java"));

        assertThat(parse(tool.execute("{\"query\":\"code\",\"limit\":1}", CONTEXT))
                .get("results")).hasSize(1);
        // absurd values must not blow up or bypass the cap
        assertThat(parse(tool.execute("{\"query\":\"code\",\"limit\":9999}", CONTEXT))
                .get("results").size()).isLessThanOrEqualTo(20);
        assertThat(parse(tool.execute("{\"query\":\"code\",\"limit\":0}", CONTEXT))
                .get("results").size()).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("results carry only a preview, not the whole body, to protect the context window")
    void contentIsPreviewed() {
        String longCode = "x".repeat(500);
        givenAllSnippets(snippet(1L, "big code", longCode, "Java"));

        var row = parse(tool.execute("{\"query\":\"big\"}", CONTEXT)).get("results").get(0);

        assertThat(row.get("contentPreview").asText().length()).isLessThan(300);
        assertThat(row.has("content")).isFalse();
    }

    @Test
    @DisplayName("search never writes: no insert/update/delete is issued")
    void searchPerformsNoWrites() {
        givenAllSnippets(snippet(1L, "x code", "code", "Java"));

        tool.execute("{\"query\":\"code\"}", CONTEXT);

        verify(snippetMapper, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(snippetMapper, never()).update(org.mockito.ArgumentMatchers.any());
        verify(snippetMapper, never()).delete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("results are scoped to the context user, never to a caller-supplied id")
    void scopedToContextUser() {
        givenAllSnippets();

        // The user id is only ever taken from ToolContext. Two lookups happen on a cold
        // cache: one to load the vector index, one for the keyword filter.
        tool.execute("{\"query\":\"code\"}", CONTEXT);

        verify(snippetMapper, atLeastOnce()).findAllByUser(USER_ID);
        verify(snippetMapper, never()).findAllByUser(argThat(id -> !USER_ID.equals(id)));
    }

    @Test
    @DisplayName("a userId supplied inside the arguments is ignored")
    void userIdInArgumentsIsIgnored() {
        givenAllSnippets();

        // A model trying to address someone else's data must have no effect: userId is
        // not a tool parameter, so it is simply an unknown key here.
        tool.execute("{\"query\":\"code\",\"userId\":999}", CONTEXT);

        verify(snippetMapper, atLeastOnce()).findAllByUser(USER_ID);
        verify(snippetMapper, never()).findAllByUser(999L);
    }
}
