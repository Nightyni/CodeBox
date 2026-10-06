package com.codebox.service;

import com.codebox.entity.Snippet;
import com.codebox.mapper.SnippetMapper;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/**
 * Aggregate view of a user's library.
 *
 * Needed because questions like "我的知识库有哪些内容" cannot be answered by vector
 * retrieval: nothing in the snippet text resembles that phrasing, so similarity
 * scoring returns an arbitrary nearest neighbour. Answering it requires counting,
 * not embedding.
 */
@Service
public class StatsService {

    private final SnippetMapper snippetMapper;

    public StatsService(SnippetMapper snippetMapper) {
        this.snippetMapper = snippetMapper;
    }

    public record LibraryStats(int total, Map<String, Integer> byLanguage,
                               List<String> topTags, List<String> titles) {}

    public LibraryStats summarise(Long userId) {
        List<Snippet> snippets = snippetMapper.findAllByUser(userId);
        Map<String, Integer> byLanguage = new TreeMap<>();
        Map<String, Integer> tagCounts = new LinkedHashMap<>();
        List<String> titles = new java.util.ArrayList<>();

        for (Snippet s : snippets) {
            String lang = s.getLanguage() == null ? "未分类" : s.getLanguage();
            byLanguage.merge(lang, 1, Integer::sum);

            if (s.getTags() != null) {
                for (String raw : s.getTags().split(",")) {
                    String tag = raw.trim();
                    if (!tag.isEmpty()) tagCounts.merge(tag, 1, Integer::sum);
                }
            }
            if (s.getTitle() != null) titles.add(s.getTitle());
        }

        List<String> topTags = tagCounts.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue(), a.getValue()))
                .limit(8)
                .map(Map.Entry::getKey)
                .toList();

        return new LibraryStats(snippets.size(), byLanguage, topTags, titles);
    }
}