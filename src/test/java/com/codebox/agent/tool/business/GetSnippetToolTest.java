package com.codebox.agent.tool.business;

import com.codebox.agent.dto.ToolResult;
import com.codebox.agent.tool.AgentToolTestSupport;
import com.codebox.agent.tool.RiskLevel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class GetSnippetToolTest extends AgentToolTestSupport {

    private GetSnippetTool tool;

    @BeforeEach
    void setUp() {
        tool = new GetSnippetTool(snippetService, json);
        givenUseCountBump();
    }

    @Test
    void isAReadTool() {
        assertThat(tool.risk()).isEqualTo(RiskLevel.READ);
        assertThat(tool.spec().name()).isEqualTo("get_snippet");
    }

    @Test
    void missingIdIsRejected() {
        ToolResult r = fail("{}", tool);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("id");
    }

    @Test
    @DisplayName("a non-numeric id is rejected instead of throwing")
    void nonNumericIdIsRejected() {
        ToolResult r = fail("{\"id\":\"abc\"}", tool);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("id");
    }

    @Test
    void malformedJsonIsRejected() {
        assertThat(fail("nonsense", tool).success()).isFalse();
    }

    @Test
    @DisplayName("a snippet owned by someone else is reported as not found, never leaked")
    void notOwnedReturnsError() {
        givenNotOwned();

        ToolResult r = tool.execute("{\"id\":42}", CONTEXT);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("找不到");
        // and crucially: no read of another user's row was attempted via a different path
        verify(snippetMapper).findById(42L, USER_ID);
    }

    @Test
    void returnsTheFullContent() {
        givenOwned(snippet(7L, "Redis 锁", "Boolean ok = redis.setIfAbsent(k, v);", "Java"));

        ToolResult r = tool.execute("{\"id\":7}", CONTEXT);

        assertThat(r.success()).isTrue();
        var node = parse(r);
        assertThat(node.get("id").asLong()).isEqualTo(7L);
        assertThat(node.get("title").asText()).isEqualTo("Redis 锁");
        assertThat(node.get("language").asText()).isEqualTo("Java");
        assertThat(node.get("content").asText()).contains("setIfAbsent");
    }

    @Test
    @DisplayName("very long content is truncated so it cannot flood the model context")
    void longContentIsTruncated() {
        givenOwned(snippet(7L, "big", "y".repeat(20000), "Java"));

        var content = parse(tool.execute("{\"id\":7}", CONTEXT)).get("content").asText();

        assertThat(content.length()).isLessThan(8200);
        assertThat(content).contains("已截断");
    }

    @Test
    void idAsStringIsAccepted() {
        givenOwned(snippet(7L, "t", "c", "Java"));

        // models sometimes send numbers as strings; that is tolerated
        assertThat(tool.execute("{\"id\":\"7\"}", CONTEXT).success()).isTrue();
    }

    @Test
    @DisplayName("reading performs no database writes")
    void readPerformsNoWrites() {
        givenOwned(snippet(7L, "t", "c", "Java"));

        tool.execute("{\"id\":7}", CONTEXT);

        verify(snippetMapper, never()).insert(org.mockito.ArgumentMatchers.any());
        verify(snippetMapper, never()).delete(anyLong(), anyLong());
    }
}
