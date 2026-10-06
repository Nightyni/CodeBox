package com.codebox.agent.tool.business;

import com.codebox.agent.dto.ToolResult;
import com.codebox.agent.dto.ToolSpec;
import com.codebox.agent.tool.AgentTool;
import com.codebox.agent.tool.RiskLevel;
import com.codebox.agent.tool.ToolContext;
import com.codebox.agent.tool.ToolJson;
import com.codebox.agent.tool.ToolSchema;
import com.codebox.entity.Snippet;
import com.codebox.rag.RetrievalService;
import com.codebox.service.SnippetService;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Finds snippets by meaning and by literal text.
 *
 * Both channels are combined on purpose: semantic recall handles "缓存代码" matching a
 * snippet titled "Spring Cache Example", while the literal pass guarantees short
 * queries like "MyBatis 分页" still hit a snippet whose body is mostly code.
 */
@Component
public class SearchSnippetsTool implements AgentTool {

    private static final int DEFAULT_LIMIT = 5;
    private static final int MAX_LIMIT = 20;
    private static final int SEMANTIC_OVERFETCH = 20;
    private static final double MIN_SEMANTIC_SCORE = 0.15;
    /** Enough of the body for the model to judge relevance without flooding the context. */
    private static final int PREVIEW_CHARS = 200;

    private final SnippetService snippetService;
    private final RetrievalService retrievalService;
    private final ToolJson json;

    public SearchSnippetsTool(SnippetService snippetService,
                              RetrievalService retrievalService,
                              ToolJson json) {
        this.snippetService = snippetService;
        this.retrievalService = retrievalService;
        this.json = json;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(
                "search_snippets",
                "按语义或关键词搜索当前用户的代码片段。用户想找某段代码时使用。"
                        + "返回片段的 id、标题、语言、标签与内容摘要；需要完整代码请再用 get_snippet。",
                ToolSchema.object(
                        ToolSchema.props()
                                .put("query", ToolSchema.string("搜索意图或关键词，例如 'MyBatis 分页' 或 '缓存'"))
                                .put("language", ToolSchema.string("可选，按语言过滤，例如 Java、SQL"))
                                .put("limit", ToolSchema.integer("可选，返回条数，默认 5，最大 20"))
                                .build(),
                        List.of("query")));
    }

    @Override
    public RiskLevel risk() {
        return RiskLevel.READ;
    }

    @Override
    public ToolResult execute(String argumentsJson, ToolContext context) {
        Map<String, Object> args = json.parse(argumentsJson);
        if (args == null) return ToolResult.error("参数不是合法 JSON");

        String query = ToolJson.asString(args.get("query"));
        if (query == null || query.isBlank()) return ToolResult.error("query 不能为空");

        String language = ToolJson.asString(args.get("language"));
        int limit = clampLimit(args.get("limit"));
        Long userId = context.userId();

        // Insertion order preserved: semantic hits first, then literal-only hits.
        Map<Long, Snippet> merged = new LinkedHashMap<>();
        Map<Long, Double> scores = new LinkedHashMap<>();

        for (RetrievalService.Scored hit : retrievalService.retrieve(
                userId, query, SEMANTIC_OVERFETCH, MIN_SEMANTIC_SCORE)) {
            merged.put(hit.snippet().getId(), hit.snippet());
            scores.put(hit.snippet().getId(), hit.score());
        }
        for (Snippet snippet : snippetService.findMatching(userId, query, MAX_LIMIT)) {
            merged.putIfAbsent(snippet.getId(), snippet);
        }

        List<Map<String, Object>> results = new ArrayList<>();
        for (Map.Entry<Long, Snippet> entry : merged.entrySet()) {
            Snippet snippet = entry.getValue();
            if (language != null && !language.isBlank()
                    && !language.equalsIgnoreCase(snippet.getLanguage())) {
                continue;
            }
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("id", snippet.getId());
            row.put("title", snippet.getTitle());
            row.put("language", snippet.getLanguage());
            row.put("tags", snippet.getTags());
            row.put("summary", snippet.getSummary());
            Double score = scores.get(entry.getKey());
            row.put("relevance", score == null ? null : Math.round(score * 10000d) / 10000d);
            row.put("contentPreview", preview(snippet.getContent()));
            results.add(row);
            if (results.size() >= limit) break;
        }

        if (results.isEmpty()) {
            Map<String, Object> empty = new LinkedHashMap<>();
            empty.put("count", 0);
            empty.put("message", "没有找到匹配的代码片段");
            empty.put("query", query);
            return json.ok(empty);
        }

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("count", results.size());
        payload.put("results", results);
        payload.put("hint", "需要完整代码请调用 get_snippet(id)。");
        return json.ok(payload);
    }

    private static int clampLimit(Object raw) {
        if (raw instanceof Number n) {
            int value = n.intValue();
            if (value < 1) return 1;
            return Math.min(value, MAX_LIMIT);
        }
        return DEFAULT_LIMIT;
    }

    private static String preview(String content) {
        if (content == null) return "";
        String flat = content.strip();
        return flat.length() <= PREVIEW_CHARS ? flat : flat.substring(0, PREVIEW_CHARS) + "...";
    }
}
