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

class DeleteSnippetToolTest extends AgentToolTestSupport {

    private DeleteSnippetTool tool;

    @BeforeEach
    void setUp() {
        tool = new DeleteSnippetTool(snippetService, json);
        givenUseCountBump();
    }

    @Test
    @DisplayName("delete is the most destructive tool, so it must be WRITE-gated")
    void isAWriteTool() {
        assertThat(tool.risk()).isEqualTo(RiskLevel.WRITE);
    }

    @Test
    void schemaRequiresIdOnly() {
        assertThat(tool.spec().parameters().toString()).contains("required=[id]");
    }

    @Test
    void missingIdIsRejected() {
        ToolResult r = fail("{}", tool);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("id");
        verify(snippetMapper, never()).delete(anyLong(), anyLong());
    }

    @Test
    void malformedJsonIsRejected() {
        assertThat(fail("not-json", tool).success()).isFalse();
        verify(snippetMapper, never()).delete(anyLong(), anyLong());
    }

    @Test
    void nonNumericIdIsRejected() {
        assertThat(fail("{\"id\":\"x\"}", tool).success()).isFalse();
        verify(snippetMapper, never()).delete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("a snippet owned by someone else is never deleted")
    void notOwnedIsRejectedAndNothingIsDeleted() {
        givenNotOwned();

        ToolResult r = tool.execute("{\"id\":5}", CONTEXT);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("找不到");
        verify(snippetMapper, never()).delete(anyLong(), anyLong());
    }

    @Test
    @DisplayName("a database-level refusal (0 rows) is reported as failure, not success")
    void zeroRowsAffectedIsFailure() {
        givenOwned(snippet(5L, "t", "c", "Java"));
        givenDeleteWorks(false);

        ToolResult r = tool.execute("{\"id\":5}", CONTEXT);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("失败");
    }

    @Test
    void successfulDeleteReportsTheTitleSoTheUserKnowsWhatWent() {
        givenOwned(snippet(5L, "Redis 分布式锁", "code", "Java"));
        givenDeleteWorks(true);

        ToolResult r = tool.execute("{\"id\":5}", CONTEXT);

        assertThat(r.success()).isTrue();
        var node = parse(r);
        assertThat(node.get("success").asBoolean()).isTrue();
        assertThat(node.get("id").asLong()).isEqualTo(5L);
        assertThat(node.get("message").asText()).contains("Redis 分布式锁");
    }

    @Test
    @DisplayName("the delete is issued with both id and user id, so ownership is enforced in SQL")
    void deleteCarriesTheOwnershipCondition() {
        givenOwned(snippet(5L, "t", "c", "Java"));
        givenDeleteWorks(true);

        tool.execute("{\"id\":5}", CONTEXT);

        verify(snippetMapper).delete(5L, USER_ID);
    }

    @Test
    @DisplayName("the vector is dropped so the deleted snippet cannot still be retrieved")
    void vectorIsRemoved() {
        givenOwned(snippet(5L, "t", "c", "Java"));
        givenDeleteWorks(true);

        tool.execute("{\"id\":5}", CONTEXT);

        verify(embeddingMapper).deleteBySnippetId(5L);
    }
}
