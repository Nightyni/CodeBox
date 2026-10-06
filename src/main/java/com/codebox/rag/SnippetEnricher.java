package com.codebox.rag;

import com.codebox.entity.Snippet;
import com.codebox.llm.ChatModel;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

/**
 * Uses the LLM to fill in tags + a one-line summary when a snippet is saved,
 * replacing the manual tagging step.
 *
 * Any failure (no key, bad JSON, timeout) leaves the fields untouched so the
 * save path never depends on the model being up.
 */
@Service
public class SnippetEnricher {

    private static final Logger log = LoggerFactory.getLogger(SnippetEnricher.class);

    private static final String SYSTEM_PROMPT = """
            你是一个代码片段归档助手。请阅读给定的代码，输出它的标签和一句话摘要。

            要求：
            1. 标签 3-6 个，用英文逗号分隔，优先使用技术名词（如 Redis、分页、Stream），不要用句子。
            2. 摘要为一句话，不超过 40 个字，说明这段代码做什么。
            3. 只输出 JSON，不要任何解释或 markdown 代码块，格式：
            {"tags": "标签1,标签2", "summary": "摘要"}
            """;

    private final ChatModel chatModel;
    private final ObjectMapper objectMapper;

    public SnippetEnricher(ChatModel chatModel, ObjectMapper objectMapper) {
        this.chatModel = chatModel;
        this.objectMapper = objectMapper;
    }

    /** Mutates the snippet in place when enrichment succeeds. */
    public void enrich(Snippet snippet) {
        if (!chatModel.available()) return;
        try {
            String userPrompt = """
                    标题: %s
                    语言: %s
                    代码:
                    %s
                    """.formatted(
                    nullSafe(snippet.getTitle()),
                    nullSafe(snippet.getLanguage()),
                    AnswerGenerator.truncate(nullSafe(snippet.getContent()), 3000));

            String raw = chatModel.complete(SYSTEM_PROMPT, userPrompt);
            JsonNode node = parseJson(raw);
            if (node == null) return;

            String tags = node.path("tags").asText("");
            String summary = node.path("summary").asText("");

            // Only fill blanks: never overwrite what the user typed by hand.
            if ((snippet.getTags() == null || snippet.getTags().isBlank()) && !tags.isBlank()) {
                snippet.setTags(tags.length() > 200 ? tags.substring(0, 200) : tags);
            }
            if ((snippet.getSummary() == null || snippet.getSummary().isBlank()) && !summary.isBlank()) {
                snippet.setSummary(summary.length() > 255 ? summary.substring(0, 255) : summary);
            }
        } catch (Exception e) {
            log.warn("Enrichment failed, keeping user-provided fields: {}", e.toString());
        }
    }

    /** Tolerates ```json fences that models habitually add. */
    JsonNode parseJson(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String text = raw.trim();
        if (text.startsWith("```")) {
            int firstNewline = text.indexOf('\n');
            if (firstNewline > 0) text = text.substring(firstNewline + 1);
            int fence = text.lastIndexOf("```");
            if (fence >= 0) text = text.substring(0, fence);
            text = text.trim();
        }
        try {
            return objectMapper.readTree(text);
        } catch (Exception e) {
            log.debug("Model did not return valid JSON: {}", text);
            return null;
        }
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }
}
