package com.codebox.rag;

import com.codebox.entity.Snippet;
import com.codebox.entity.SnippetEmbedding;
import com.codebox.mapper.SnippetEmbeddingMapper;
import com.codebox.mapper.SnippetMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Retrieval correctness, with emphasis on the silent-failure modes.
 *
 * The important property here is that vectors produced by a *different* embedding model
 * are not compared against the current one: cosine similarity across mismatched widths is
 * meaningless, and before this guard the only symptom was a quietly wrong ranking.
 */
class RetrievalServiceTest {

    private static final Long USER = 1L;

    private SnippetMapper snippetMapper;
    private SnippetEmbeddingMapper embeddingMapper;
    private VectorMath vectorMath = new VectorMath();
    private RetrievalService service;

    /** Embedding model with a controllable width, so dimension changes can be simulated. */
    static class FixedWidthEmbeddingModel implements EmbeddingModel {
        private final int width;
        FixedWidthEmbeddingModel(int width) { this.width = width; }

        @Override
        public float[] embed(String text) {
            float[] v = new float[width];
            // deterministic, non-zero, and stable per text
            int h = text == null ? 0 : text.hashCode();
            for (int i = 0; i < width; i++) v[i] = ((h >> (i % 24)) & 0xFF) / 255f;
            if (width > 0) v[0] = Math.abs(v[0]) + 0.1f;
            return v;
        }

        @Override
        public String name() { return "fixed-" + width; }

        @Override
        public int dimensions() { return width; }
    }

    private void build(int width) {
        snippetMapper = mock(SnippetMapper.class);
        embeddingMapper = mock(SnippetEmbeddingMapper.class);
        service = new RetrievalService(snippetMapper, embeddingMapper,
                new FixedWidthEmbeddingModel(width), vectorMath);
    }

    private Snippet snippet(long id, String title) {
        Snippet s = new Snippet();
        s.setId(id);
        s.setTitle(title);
        s.setLanguage("Java");
        s.setTags("t");
        s.setContent("code " + title);
        return s;
    }

    private SnippetEmbedding stored(long snippetId, float[] vector) {
        SnippetEmbedding e = new SnippetEmbedding();
        e.setSnippetId(snippetId);
        e.setUserId(USER);
        e.setModel("whatever");
        e.setVector(vectorMath.serialize(vector));
        e.setDimensions(vector.length);
        return e;
    }

    // ---------------------------------------------------------------- basics

    @Test
    void returnsTopKByCosine() {
        build(8);
        when(snippetMapper.findAllByUser(USER)).thenReturn(List.of(
                snippet(1L, "aaa"), snippet(2L, "bbb"), snippet(3L, "ccc")));

        var model = new FixedWidthEmbeddingModel(8);
        when(embeddingMapper.findByUser(USER)).thenReturn(List.of(
                stored(1L, model.embed("aaa")),
                stored(2L, model.embed("bbb")),
                stored(3L, model.embed("ccc"))));

        var hits = service.retrieve(USER, "aaa", 2, -1.0);

        assertThat(hits).hasSize(2);
        assertThat(hits.get(0).snippet().getId()).isEqualTo(1L);   // exact match first
        assertThat(hits.get(0).score()).isGreaterThan(hits.get(1).score());
    }

    @Test
    void respectsMinScore() {
        build(8);
        when(snippetMapper.findAllByUser(USER)).thenReturn(List.of(snippet(1L, "aaa")));
        when(embeddingMapper.findByUser(USER)).thenReturn(List.of());

        // nothing is indexed, so nothing can clear the threshold
        assertThat(service.retrieve(USER, "aaa", 5, 0.5)).isEmpty();
    }

    @Test
    void snippetsOutsideTheUserScopeAreNeverReturned() {
        build(8);
        // the cache holds a vector but the snippet is not in this user's library
        var model = new FixedWidthEmbeddingModel(8);
        when(snippetMapper.findAllByUser(USER)).thenReturn(List.of());
        when(embeddingMapper.findByUser(USER)).thenReturn(List.of(stored(99L, model.embed("x"))));

        assertThat(service.retrieve(USER, "x", 5, -1.0)).isEmpty();
    }

    // ---------------------------------------------------------------- dimension guard

    @Test
    @DisplayName("vectors from a different embedding width are skipped, not compared")
    void incompatibleWidthsAreSkipped() {
        build(8);
        when(snippetMapper.findAllByUser(USER)).thenReturn(List.of(
                snippet(1L, "aaa"), snippet(2L, "bbb")));

        // stored vectors are 16 wide while the current model produces 8
        var other = new FixedWidthEmbeddingModel(16);
        when(embeddingMapper.findByUser(USER)).thenReturn(List.of(
                stored(1L, other.embed("aaa")),
                stored(2L, other.embed("bbb"))));

        var hits = service.retrieve(USER, "aaa", 5, -1.0);

        assertThat(hits).isEmpty();
    }

    @Test
    @DisplayName("a mixed cache keeps the compatible vectors and drops the rest")
    void mixedWidthsKeepTheCompatibleOnes() {
        build(8);
        when(snippetMapper.findAllByUser(USER)).thenReturn(List.of(
                snippet(1L, "aaa"), snippet(2L, "bbb")));

        var current = new FixedWidthEmbeddingModel(8);
        var other = new FixedWidthEmbeddingModel(16);
        when(embeddingMapper.findByUser(USER)).thenReturn(List.of(
                stored(1L, current.embed("aaa")),   // comparable
                stored(2L, other.embed("bbb"))));   // stale width

        var hits = service.retrieve(USER, "aaa", 5, -1.0);

        assertThat(hits).hasSize(1);
        assertThat(hits.get(0).snippet().getId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("reindexAll rebuilds vectors and restores retrieval after a model change")
    void reindexRestoresRetrievalAfterModelChange() {
        build(8);
        when(snippetMapper.findAllByUser(USER)).thenReturn(List.of(snippet(1L, "aaa")));

        // start with an incompatible stored vector
        var other = new FixedWidthEmbeddingModel(16);
        when(embeddingMapper.findByUser(USER)).thenReturn(List.of(stored(1L, other.embed("aaa"))));
        assertThat(service.retrieve(USER, "aaa", 5, -1.0)).isEmpty();

        // after a rebuild the stored width matches the current model again
        int indexed = service.reindexAll(USER);
        assertThat(indexed).isEqualTo(1);

        var current = new FixedWidthEmbeddingModel(8);
        when(embeddingMapper.findByUser(USER)).thenReturn(List.of(stored(1L, current.embed("aaa"))));

        assertThat(service.retrieve(USER, "aaa", 5, -1.0)).hasSize(1);
    }

    @Test
    @DisplayName("the reported embedding model name comes from the model in use")
    void reportsEmbeddingModelName() {
        build(8);
        assertThat(service.embeddingModelName()).isEqualTo("fixed-8");
    }
}
