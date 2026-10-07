package com.codebox.agent.tool.business;

import com.codebox.agent.dto.ToolResult;
import com.codebox.agent.tool.AgentToolTestSupport;
import com.codebox.agent.tool.RiskLevel;
import com.codebox.entity.Snippet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class UpdateSnippetToolTest extends AgentToolTestSupport {

    private UpdateSnippetTool tool;

    @BeforeEach
    void setUp() {
        tool = new UpdateSnippetTool(snippetService, json);
        givenUseCountBump();
        givenUpdateWorks();
    }

    @Test
    @DisplayName("update is a WRITE tool because it overwrites existing content")
    void isAWriteTool() {
        assertThat(tool.risk()).isEqualTo(RiskLevel.WRITE);
    }

    @Test
    void missingIdIsRejected() {
        ToolResult r = fail("{\"title\":\"new\"}", tool);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("id");
        verify(snippetMapper, never()).update(any());
    }

    @Test
    void malformedJsonIsRejected() {
        assertThat(fail("[]", tool).success()).isFalse();
    }

    @Test
    @DisplayName("a snippet owned by someone else is never modified")
    void notOwnedIsRejected() {
        givenNotOwned();

        ToolResult r = tool.execute("{\"id\":5,\"title\":\"hacked\"}", CONTEXT);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("找不到");
        verify(snippetMapper, never()).update(any());
    }

    @Test
    @DisplayName("omitted fields keep their existing values instead of being blanked")
    void omittedFieldsKeepExistingValues() {
        givenOwned(snippet(5L, "原标题", "原代码", "Java"));

        ToolResult r = tool.execute("{\"id\":5,\"tags\":\"新标签\"}", CONTEXT);

        assertThat(r.success()).isTrue();
        ArgumentCaptor<Snippet> captor = ArgumentCaptor.forClass(Snippet.class);
        verify(snippetMapper).update(captor.capture());
        Snippet saved = captor.getValue();

        assertThat(saved.getTitle()).isEqualTo("原标题");
        assertThat(saved.getContent()).isEqualTo("原代码");
        assertThat(saved.getLanguage()).isEqualTo("Java");
        assertThat(saved.getTags()).isEqualTo("新标签");
    }

    @Test
    void suppliedFieldsAreApplied() {
        givenOwned(snippet(5L, "原标题", "原代码", "Java"));

        tool.execute("{\"id\":5,\"title\":\"新标题\",\"content\":\"新代码\"}", CONTEXT);

        ArgumentCaptor<Snippet> captor = ArgumentCaptor.forClass(Snippet.class);
        verify(snippetMapper).update(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("新标题");
        assertThat(captor.getValue().getContent()).isEqualTo("新代码");
    }

    @Test
    @DisplayName("over-long title is rejected before hitting the database")
    void overLongTitleIsRejected() {
        givenOwned(snippet(5L, "原标题", "原代码", "Java"));

        ToolResult r = tool.execute(
                "{\"id\":5,\"title\":\"" + "x".repeat(101) + "\"}",
                CONTEXT);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("100");
        verify(snippetMapper, never()).update(any());
    }

    @Test
    @DisplayName("over-long content is rejected before hitting the database")
    void overLongContentIsRejected() {
        givenOwned(snippet(5L, "原标题", "原代码", "Java"));

        ToolResult r = tool.execute(
                "{\"id\":5,\"content\":\"" + "x".repeat(20001) + "\"}",
                CONTEXT);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("20000");
        verify(snippetMapper, never()).update(any());
    }

    @Test
    @DisplayName("over-long language is rejected before hitting the database")
    void overLongLanguageIsRejected() {
        givenOwned(snippet(5L, "原标题", "原代码", "Java"));

        ToolResult r = tool.execute(
                "{\"id\":5,\"language\":\"" + "x".repeat(31) + "\"}",
                CONTEXT);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("30");
        verify(snippetMapper, never()).update(any());
    }

    @Test
    @DisplayName("over-long tags are rejected before hitting the database")
    void overLongTagsIsRejected() {
        givenOwned(snippet(5L, "原标题", "原代码", "Java"));

        ToolResult r = tool.execute(
                "{\"id\":5,\"tags\":\"" + "x".repeat(201) + "\"}",
                CONTEXT);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("200");
        verify(snippetMapper, never()).update(any());
    }

    @Test
    @DisplayName("a blank title in the arguments falls back to the existing one")
    void blankTitleFallsBackToExisting() {
        givenOwned(snippet(5L, "原标题", "原代码", "Java"));

        tool.execute("{\"id\":5,\"title\":\"   \"}", CONTEXT);

        ArgumentCaptor<Snippet> captor = ArgumentCaptor.forClass(Snippet.class);
        verify(snippetMapper).update(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("原标题");
    }

    @Test
    void nonNumericIdIsRejected() {
        ToolResult r = fail("{\"id\":\"not-a-number\"}", tool);

        assertThat(r.success()).isFalse();
        verify(snippetMapper, never()).update(any());
    }

    @Test
    @DisplayName("the update is scoped to the context user at the mapper level")
    void updateIsScopedToUser() {
        givenOwned(snippet(5L, "t", "c", "Java"));

        tool.execute("{\"id\":5,\"title\":\"new\"}", CONTEXT);

        // Ownership is enforced in SQL: every lookup carries the context user id, and
        // the tool never trusts the raw id on its own. Two lookups happen - one in the
        // tool (to merge omitted fields) and one inside the service's update path.
        verify(snippetMapper, atLeastOnce()).findById(5L, USER_ID);
        verify(snippetMapper, never()).findById(anyLong(), argThat(id -> !USER_ID.equals(id)));
    }

    @Test
    @DisplayName("the updated snippet is re-indexed so search reflects the new content")
    void updatedSnippetIsReindexed() {
        givenOwned(snippet(5L, "t", "c", "Java"));

        tool.execute("{\"id\":5,\"content\":\"completely new body\"}", CONTEXT);

        verify(embeddingMapper, org.mockito.Mockito.atLeastOnce())
                .upsert(any(com.codebox.entity.SnippetEmbedding.class));
    }

    @Test
    void successfulUpdateReportsTheTitle() {
        givenOwned(snippet(5L, "原标题", "c", "Java"));

        ToolResult r = tool.execute("{\"id\":5,\"title\":\"改后\"}", CONTEXT);

        assertThat(r.success()).isTrue();
        assertThat(parse(r).get("message").asText()).contains("改后");
    }
}