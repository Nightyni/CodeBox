package com.codebox.service;

import com.codebox.dto.PageResponse;
import com.codebox.dto.SnippetRequest;
import com.codebox.entity.Snippet;
import com.codebox.mapper.SnippetMapper;
import com.codebox.rag.RetrievalService;
import com.codebox.rag.SnippetEnricher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SnippetServiceTest {

    private SnippetMapper mapper;
    private SnippetEnricher enricher;
    private RetrievalService retrieval;
    private SnippetService service;

    @BeforeEach
    void setUp() {
        mapper = mock(SnippetMapper.class);
        enricher = mock(SnippetEnricher.class);
        retrieval = mock(RetrievalService.class);
        service = new SnippetService(mapper, enricher, retrieval);
    }

    // ---------- pagination bounds: the bug that made ?pageSize=0 500 the old app ----------

    @Test
    @DisplayName("pageSize=0 is clamped to the default instead of dividing by zero")
    void zeroPageSizeIsClamped() {
        when(mapper.search(anyLong(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(mapper.countSearch(anyLong(), any(), any(), any())).thenReturn(0L);

        PageResponse<Snippet> page = service.search(1L, null, null, null, 1, 0);

        assertThat(page.pageSize()).isEqualTo(SnippetService.DEFAULT_PAGE_SIZE);
    }

    @Test
    @DisplayName("oversized pageSize is capped so one request cannot dump the table")
    void hugePageSizeIsCapped() {
        when(mapper.search(anyLong(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(mapper.countSearch(anyLong(), any(), any(), any())).thenReturn(0L);

        PageResponse<Snippet> page = service.search(1L, null, null, null, 1, 100_000);

        assertThat(page.pageSize()).isEqualTo(SnippetService.MAX_PAGE_SIZE);
    }

    @Test
    @DisplayName("negative pageNum falls back to page 1 rather than a negative SQL offset")
    void negativePageNumFallsBack() {
        when(mapper.search(anyLong(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(mapper.countSearch(anyLong(), any(), any(), any())).thenReturn(0L);

        ArgumentCaptor<Integer> offset = ArgumentCaptor.forClass(Integer.class);
        PageResponse<Snippet> page = service.search(1L, null, null, null, -5, 10);

        verify(mapper).search(eq(1L), any(), any(), any(), offset.capture(), eq(10));
        assertThat(page.pageNum()).isEqualTo(1);
        assertThat(offset.getValue()).isZero();
    }

    @Test
    void totalPagesIsCeilOfCountOverSize() {
        when(mapper.search(anyLong(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(mapper.countSearch(anyLong(), any(), any(), any())).thenReturn(25L);

        PageResponse<Snippet> page = service.search(1L, null, null, null, 1, 10);

        assertThat(page.totalPages()).isEqualTo(3);
        assertThat(page.totalCount()).isEqualTo(25L);
    }

    @Test
    @DisplayName("blank filters are normalised to null so the SQL <if> blocks skip them")
    void blankFiltersBecomeNull() {
        when(mapper.search(anyLong(), any(), any(), any(), anyInt(), anyInt())).thenReturn(List.of());
        when(mapper.countSearch(anyLong(), any(), any(), any())).thenReturn(0L);

        service.search(1L, "   ", "", "  ", 1, 10);

        verify(mapper).search(eq(1L), isNull(), isNull(), isNull(), anyInt(), anyInt());
    }

    // ---------- ownership ----------

    @Test
    @DisplayName("incrementUseCount is scoped by userId - a user cannot bump someone else's counter")
    void useCountIncrementIsScopedToOwner() {
        Snippet found = new Snippet();
        found.setId(9L);
        found.setUseCount(4);
        when(mapper.findById(9L, 1L)).thenReturn(found);

        Snippet result = service.findById(9L, 1L);

        // both id AND userId must reach the mapper
        verify(mapper).incrementUseCount(9L, 1L);
        assertThat(result.getUseCount()).isEqualTo(5);
    }

    @Test
    void findByIdReturnsNullWhenNotOwned() {
        when(mapper.findById(9L, 1L)).thenReturn(null);

        assertThat(service.findById(9L, 1L)).isNull();
        verify(mapper, never()).incrementUseCount(anyLong(), anyLong());
    }

    // ---------- create / update / delete ----------

    @Test
    @DisplayName("create enriches, persists and indexes the new snippet")
    void createEnrichesAndIndexes() {
        SnippetRequest req = new SnippetRequest();
        req.setTitle("  分页查询  ");
        req.setContent("SELECT 1");
        req.setLanguage("SQL");
        req.setTags("  ");

        Snippet created = service.create(1L, req);

        assertThat(created.getTitle()).isEqualTo("分页查询");   // trimmed
        assertThat(created.getTags()).isNull();                  // blank -> null
        verify(enricher).enrich(any(Snippet.class));
        verify(mapper).insert(any(Snippet.class));
        verify(retrieval).index(eq(1L), any(Snippet.class));
    }

    @Test
    void updateReturnsNullForSnippetOwnedBySomebodyElse() {
        when(mapper.findById(5L, 1L)).thenReturn(null);

        SnippetRequest req = new SnippetRequest();
        req.setTitle("t");
        req.setContent("c");
        req.setLanguage("Java");

        assertThat(service.update(1L, 5L, req)).isNull();
        verify(mapper, never()).update(any());
    }

    @Test
    void deleteAlsoDropsTheVector() {
        when(mapper.delete(5L, 1L)).thenReturn(1);

        assertThat(service.delete(1L, 5L)).isTrue();
        verify(retrieval).remove(5L);
    }

    @Test
    @DisplayName("a failed delete must not evict the vector")
    void failedDeleteKeepsVector() {
        when(mapper.delete(5L, 1L)).thenReturn(0);

        assertThat(service.delete(1L, 5L)).isFalse();
        verify(retrieval, never()).remove(anyLong());
    }
}
