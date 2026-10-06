package com.codebox.rag;

import com.codebox.dto.AskResponse;
import com.codebox.llm.ChatModel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

/**
 * Answers a natural-language question strictly from retrieved snippets.
 *
 * Grounding rules encoded in the prompt (and enforced by the caller):
 *  - only use the numbered CONTEXT blocks, never outside knowledge
 *  - every claim must cite [n]
 *  - if the context is insufficient, say so instead of guessing
 */
@Service
public class AnswerGenerator {

    private static final Logger log = LoggerFactory.getLogger(AnswerGenerator.class);

    static final String INSUFFICIENT_MARKER = "INSUFFICIENT_EVIDENCE";

    private static final String SYSTEM_PROMPT = """
            你是一个代码知识库助手。你只能依据用户提供的 CONTEXT 片段回答问题。

            严格遵守以下规则：
            1. 只使用 CONTEXT 中出现的信息，禁止使用你自己的先验知识补充。
            2. 每个结论后面必须标注来源编号，格式如 [1]、[2]。
            3. 如果 CONTEXT 中的片段不足以回答问题，只返回一行：INSUFFICIENT_EVIDENCE，不要编造。
            4. 如果 CONTEXT 里有可直接复用的代码，请用 ``` 代码块给出，并注明来源编号。
            5. 用中文回答，简洁、直接，不要复述问题。
            """;

    private final ChatModel chatModel;

    public AnswerGenerator(ChatModel chatModel) {
        this.chatModel = chatModel;
    }

    public AskResponse answer(String question, List<RetrievalService.Scored> hits) {
        long start = System.currentTimeMillis();
        List<AskResponse.Citation> citations = new ArrayList<>();
        for (RetrievalService.Scored hit : hits) {
            citations.add(new AskResponse.Citation(
                    hit.snippet().getId(),
                    hit.snippet().getTitle(),
                    hit.snippet().getLanguage(),
                    round(hit.score())));
        }

        if (hits.isEmpty()) {
            return new AskResponse(question, "知识库里没有检索到相关内容，暂时无法回答。",
                    citations, true, false, System.currentTimeMillis() - start);
        }
        if (!chatModel.available()) {
            // Tell the user exactly what to do, instead of leaving them guessing why
            // no answer appeared.
            return new AskResponse(question,
                    "还没有配置大模型 API Key，所以无法生成回答。\n"
                            + "把 key 填到 config/application-local.yml 的 codebox.llm.api-key，重启应用即可。\n\n"
                            + "检索本身是正常的，下面 " + hits.size() + " 条是最相关的片段，可以直接点开查看。",
                    citations, false, false, System.currentTimeMillis() - start);
        }

        String userPrompt = buildPrompt(question, hits);
        String raw = chatModel.complete(SYSTEM_PROMPT, userPrompt);
        long elapsed = System.currentTimeMillis() - start;

        // A failed call must not be reported as "the knowledge base lacks this".
        if (chatModel.lastCallFailed()) {
            return new AskResponse(question,
                    "大模型调用失败（常见原因：API Key 无效/过期、余额不足、网络不通或超时）。"
                            + "检索本身正常，已返回 " + hits.size() + " 条相关片段。",
                    citations, false, true, elapsed);
        }
        if (raw == null || raw.isBlank() || raw.contains(INSUFFICIENT_MARKER)) {
            return new AskResponse(question, "知识库中的片段不足以回答这个问题。",
                    citations, true, false, elapsed);
        }
        return new AskResponse(question, raw.trim(), citations, false, false, elapsed);
    }

    private String buildPrompt(String question, List<RetrievalService.Scored> hits) {
        StringJoiner ctx = new StringJoiner("\n\n");
        int i = 1;
        for (RetrievalService.Scored hit : hits) {
            ctx.add("[%d] 标题: %s | 语言: %s | 标签: %s%n%s".formatted(
                    i++,
                    nullSafe(hit.snippet().getTitle()),
                    nullSafe(hit.snippet().getLanguage()),
                    nullSafe(hit.snippet().getTags()),
                    truncate(nullSafe(hit.snippet().getContent()), 1200)));
        }
        return "CONTEXT:\n" + ctx + "\n\n问题: " + question;
    }

    private static String nullSafe(String s) { return s == null ? "" : s; }

    /**
     * Bounds long content before it reaches a model prompt.
     *
     * Public because the agent tools need the same truncation; keeping one
     * implementation avoids the two paths drifting apart.
     */
    public static String truncate(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "\n... (已截断)";
    }

    private static double round(double v) {
        return Math.round(v * 10000d) / 10000d;
    }
}
