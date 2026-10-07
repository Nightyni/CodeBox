package com.codebox.service;

import com.codebox.dto.PageResponse;
import com.codebox.dto.SnippetRequest;
import com.codebox.entity.Snippet;
import com.codebox.mapper.SnippetMapper;
import com.codebox.rag.RetrievalService;
import com.codebox.rag.SnippetEnricher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class SnippetService {

    /** Hard bounds: pageSize is attacker-controlled, so it can never be trusted. */
    public static final int MAX_PAGE_SIZE = 100;
    public static final int DEFAULT_PAGE_SIZE = 10;

    private final SnippetMapper snippetMapper;
    private final SnippetEnricher enricher;
    private final RetrievalService retrievalService;

    public SnippetService(SnippetMapper snippetMapper,
                          SnippetEnricher enricher,
                          RetrievalService retrievalService) {
        this.snippetMapper = snippetMapper;
        this.enricher = enricher;
        this.retrievalService = retrievalService;
    }

    public Snippet findById(Long id, Long userId) {
        Snippet snippet = snippetMapper.findById(id, userId);

        if (snippet != null) {
            // Ownership is enforced in SQL, so this cannot bump another user's counter.
            snippetMapper.incrementUseCount(id, userId);
            snippet.setUseCount(
                    (snippet.getUseCount() == null ? 0 : snippet.getUseCount()) + 1
            );
        }

        return snippet;
    }

    public PageResponse<Snippet> search(Long userId,
                                        String keyword,
                                        String language,
                                        String tags,
                                        Integer pageNum,
                                        Integer pageSize) {
        int safePage = clampPage(pageNum);
        int safeSize = clampSize(pageSize);
        int offset = (safePage - 1) * safeSize;

        List<Snippet> rows = snippetMapper.search(
                userId,
                blankToNull(keyword),
                blankToNull(language),
                blankToNull(tags),
                offset,
                safeSize
        );

        long total = snippetMapper.countSearch(
                userId,
                blankToNull(keyword),
                blankToNull(language),
                blankToNull(tags)
        );

        return PageResponse.of(rows, safePage, safeSize, total);
    }

    @Transactional
    public Snippet create(Long userId, SnippetRequest request) {
        Snippet snippet = new Snippet();

        snippet.setUserId(userId);
        snippet.setTitle(request.getTitle().trim());
        snippet.setContent(request.getContent());
        snippet.setLanguage(request.getLanguage().trim());
        snippet.setTags(blankToNull(request.getTags()));

        enricher.enrich(snippet);
        snippetMapper.insert(snippet);

        retrievalService.index(userId, snippet);

        return snippet;
    }

    @Transactional
    public Snippet update(Long userId, Long id, SnippetRequest request) {
        Snippet existing = snippetMapper.findById(id, userId);

        if (existing == null) {
            return null;
        }

        existing.setTitle(request.getTitle().trim());
        existing.setContent(request.getContent());
        existing.setLanguage(request.getLanguage().trim());
        existing.setTags(blankToNull(request.getTags()));

        // Re-derive tags/summary only when the user cleared the fields.
        if (existing.getTags() == null) {
            enricher.enrich(existing);
        }

        snippetMapper.update(existing);
        retrievalService.index(userId, existing);

        return existing;
    }

    @Transactional
    public boolean delete(Long userId, Long id) {
        boolean removed = snippetMapper.delete(id, userId) > 0;

        if (removed) {
            retrievalService.remove(userId, id);
        }

        return removed;
    }

    // ---------- helpers ----------

    /**
     * Keyword pre-filter used by the agent's hybrid search.
     *
     * Semantic retrieval alone misses short lookups such as "MyBatis 分页" when the
     * snippet body is mostly code, so the agent unions a literal match over
     * title/summary/tags/content. Performed in memory because the agent always scans
     * the whole (small) personal library anyway, and this stays database-portable.
     */
    public List<Snippet> findMatching(Long userId, String keyword, int limit) {
        List<Snippet> out = new java.util.ArrayList<>();

        if (keyword == null || keyword.isBlank()) {
            return out;
        }

        String needle = keyword.toLowerCase();

        for (Snippet snippet : snippetMapper.findAllByUser(userId)) {
            if (contains(snippet.getTitle(), needle)
                    || contains(snippet.getSummary(), needle)
                    || contains(snippet.getTags(), needle)
                    || contains(snippet.getLanguage(), needle)
                    || contains(snippet.getContent(), needle)) {

                out.add(snippet);

                if (out.size() >= limit) {
                    break;
                }
            }
        }

        return out;
    }

    private static boolean contains(String haystack, String lowercaseNeedle) {
        return haystack != null
                && haystack.toLowerCase().contains(lowercaseNeedle);
    }

    static int clampPage(Integer pageNum) {
        if (pageNum == null || pageNum < 1) {
            return 1;
        }

        return Math.min(pageNum, 10_000);
    }

    static int clampSize(Integer pageSize) {
        if (pageSize == null || pageSize < 1) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(pageSize, MAX_PAGE_SIZE);
    }

    private static String blankToNull(String s) {
        return (s == null || s.isBlank())
                ? null
                : s.trim();
    }
}