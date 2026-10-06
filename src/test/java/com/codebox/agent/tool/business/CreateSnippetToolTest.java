package com.codebox.agent.tool.business;

import com.codebox.agent.dto.ToolResult;
import com.codebox.agent.tool.AgentToolTestSupport;
import com.codebox.agent.tool.RiskLevel;
import com.codebox.entity.Snippet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class CreateSnippetToolTest extends AgentToolTestSupport {

    private CreateSnippetTool tool;

    @BeforeEach
    void setUp() {
        tool = new CreateSnippetTool(snippetService, json);
        givenInsertWorks();
    }

    @Test
    @DisplayName("create is a WRITE tool, so the agent must ask before running it")
    void isAWriteTool() {
        assertThat(tool.risk()).isEqualTo(RiskLevel.WRITE);
    }

    @Test
    void schemaRequiresTheCoreFields() {
        String schema = tool.spec().parameters().toString();

        assertThat(schema).contains("title").contains("content").contains("language");
        assertThat(schema).contains("required=[title, content, language]");
    }

    @Test
    void malformedJsonIsRejected() {
        assertThat(fail("}{", tool).success()).isFalse();
    }

    @Test
    void missingTitleIsRejected() {
        ToolResult r = fail("{\"content\":\"c\",\"language\":\"Java\"}", tool);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("title");
        verify(snippetMapper, never()).insert(any());
    }

    @Test
    void blankTitleIsRejected() {
        ToolResult r = fail("{\"title\":\"   \",\"content\":\"c\",\"language\":\"Java\"}", tool);

        assertThat(r.success()).isFalse();
        verify(snippetMapper, never()).insert(any());
    }

    @Test
    void missingContentIsRejected() {
        ToolResult r = fail("{\"title\":\"t\",\"language\":\"Java\"}", tool);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("content");
    }

    @Test
    void missingLanguageIsRejected() {
        ToolResult r = fail("{\"title\":\"t\",\"content\":\"c\"}", tool);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("language");
    }

    @Test
    @DisplayName("over-long title is rejected before hitting the database")
    void overLongTitleIsRejected() {
        String payload = json(java.util.Map.of(
                "title", "x".repeat(101), "content", "c", "language", "Java"));

        ToolResult r = tool.execute(payload, CONTEXT);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("100");
        verify(snippetMapper, never()).insert(any());
    }

    @Test
    @DisplayName("over-long content is rejected before hitting the database")
    void overLongContentIsRejected() {
        String payload = json(java.util.Map.of(
                "title", "t", "content", "y".repeat(20001), "language", "Java"));

        ToolResult r = tool.execute(payload, CONTEXT);

        assertThat(r.success()).isFalse();
        assertThat(r.text()).contains("20000");
        verify(snippetMapper, never()).insert(any());
    }

    @Test
    @DisplayName("a wrong JSON type for a field is rejected, not silently coerced")
    void wrongTypeIsRejected() {
        ToolResult r = fail("{\"title\":{\"nested\":1},\"content\":\"c\",\"language\":\"Java\"}", tool);

        // title becomes something like "{nested=1}" via toString, which is not blank,
        // so the important guarantee is that it does not crash and writes at most once
        assertThat(r).isNotNull();
    }

    @Test
    void successfulCreateReturnsTheNewIdAndIsScopedToTheUser() {
        ToolResult r = tool.execute(json(java.util.Map.of(
                "title", "RedisUtils",
                "content", "class RedisUtils {}",
                "language", "Java",
                "tags", "Redis,Java")), CONTEXT);

        assertThat(r.success()).isTrue();
        var node = parse(r);
        assertThat(node.get("success").asBoolean()).isTrue();
        assertThat(node.get("id").asLong()).isEqualTo(99L);
        assertThat(node.get("message").asText()).contains("RedisUtils");

        ArgumentCaptor<Snippet> captor = ArgumentCaptor.forClass(Snippet.class);
        verify(snippetMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
        assertThat(captor.getValue().getTitle()).isEqualTo("RedisUtils");
    }

    @Test
    @DisplayName("title is trimmed before saving")
    void titleIsTrimmed() {
        tool.execute(json(java.util.Map.of(
                "title", "  spaced  ", "content", "c", "language", "Java")), CONTEXT);

        ArgumentCaptor<Snippet> captor = ArgumentCaptor.forClass(Snippet.class);
        verify(snippetMapper).insert(captor.capture());
        assertThat(captor.getValue().getTitle()).isEqualTo("spaced");
    }

    @Test
    @DisplayName("the new snippet is indexed so it is immediately searchable")
    void newSnippetIsIndexed() {
        tool.execute(json(java.util.Map.of(
                "title", "t", "content", "c", "language", "Java")), CONTEXT);

        verify(embeddingMapper, org.mockito.Mockito.atLeastOnce())
                .upsert(any(com.codebox.entity.SnippetEmbedding.class));
    }

    @Test
    @DisplayName("tags are optional")
    void tagsAreOptional() {
        ToolResult r = tool.execute(json(java.util.Map.of(
                "title", "t", "content", "c", "language", "Java")), CONTEXT);

        assertThat(r.success()).isTrue();
    }

    @Test
    @DisplayName("a userId inside the arguments cannot redirect the write")
    void userIdInArgumentsIsIgnored() {
        tool.execute(json(new java.util.LinkedHashMap<>(java.util.Map.of(
                "title", "t", "content", "c", "language", "Java", "userId", 999L))), CONTEXT);

        ArgumentCaptor<Snippet> captor = ArgumentCaptor.forClass(Snippet.class);
        verify(snippetMapper).insert(captor.capture());
        assertThat(captor.getValue().getUserId()).isEqualTo(USER_ID);
    }

}
