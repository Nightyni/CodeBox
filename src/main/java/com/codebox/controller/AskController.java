package com.codebox.controller;

import com.codebox.dto.AskResponse;
import com.codebox.entity.User;
import com.codebox.rag.AnswerGenerator;
import com.codebox.rag.LibraryQuestion;
import com.codebox.rag.RetrievalService;
import com.codebox.service.AuthService;
import com.codebox.service.StatsService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Natural-language Q&A over the user's own snippet library. */
@RestController
@RequestMapping("/api/ask")
public class AskController extends BaseController {

    /** Retrieved context size; higher recalls more but dilutes the prompt. */
    private static final int TOP_K = 5;
    /**
     * Cosine floor below which a hit is treated as noise rather than context.
     *
     * Measured on the built-in local vectorizer: a genuinely relevant query scores
     * ~0.5 (e.g. "redis 分布式锁" -> 0.53), while unrelated text scores ~0.06-0.12.
     * 0.05 let pure noise through and presented it to the user as a citation.
     */
    private static final double MIN_SCORE = 0.18;

    private final RetrievalService retrievalService;
    private final AnswerGenerator answerGenerator;
    private final StatsService statsService;

    public AskController(AuthService authService,
                         RetrievalService retrievalService,
                         AnswerGenerator answerGenerator,
                         StatsService statsService) {
        super(authService);
        this.retrievalService = retrievalService;
        this.answerGenerator = answerGenerator;
        this.statsService = statsService;
    }

    /**
     * Answers "what is in my knowledge base" questions with real counts.
     *
     * Vector search cannot answer these: the phrasing does not resemble any snippet,
     * so similarity returns an arbitrary neighbour. Counting is the correct tool.
     *
     * @return null when the question is not a library-overview question
     */
    private AskResponse libraryOverview(Long userId, String question) {
        if (!LibraryQuestion.isOverview(question)) return null;

        StatsService.LibraryStats stats = statsService.summarise(userId);
        if (stats.total() == 0) {
            return new AskResponse(question, "你的知识库目前是空的，先去「+ 新增」存几条代码吧。",
                    List.of(), false, false, 0);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("你的知识库共有 ").append(stats.total()).append(" 条代码片段。\n\n");
        sb.append("按语言分布：");
        stats.byLanguage().forEach((lang, n) -> sb.append(lang).append(" ").append(n).append(" 条、"));
        sb.setLength(sb.length() - 1);
        sb.append('\n');
        if (!stats.topTags().isEmpty()) {
            sb.append("\n高频标签：").append(String.join("、", stats.topTags())).append('\n');
        }
        sb.append("\n全部标题：\n");
        for (String title : stats.titles()) sb.append("  · ").append(title).append('\n');

        return new AskResponse(question, sb.toString().trim(), List.of(), false, false, 0);
    }

    @PostMapping
    public AskResponse ask(HttpSession session, @RequestBody Map<String, String> body) {
        User user = currentUser(session);
        String question = body == null ? null : body.get("question");
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        String trimmed = question.trim();

        // Overview questions are answered by counting, not by similarity search.
        AskResponse overview = libraryOverview(user.getId(), trimmed);
        if (overview != null) return overview;

        List<RetrievalService.Scored> hits =
                retrievalService.retrieve(user.getId(), trimmed, TOP_K, MIN_SCORE);
        return answerGenerator.answer(trimmed, hits);
    }

    /**
     * Rebuilds the vector index for the current user.
     *
     * Required once after importing snippets directly into the database, otherwise
     * those rows have no embedding and natural-language search cannot find them.
     */
    @PostMapping("/reindex")
    public Map<String, Object> reindex(HttpSession session) {
        User user = currentUser(session);
        int indexed = retrievalService.reindexAll(user.getId());
        return Map.of("success", true, "indexed", indexed,
                "embeddingModel", retrievalService.embeddingModelName());
    }

    /** Retrieval only - useful for debugging why the model saw (or missed) something. */
    @PostMapping("/retrieve")
    public List<Map<String, Object>> retrieve(HttpSession session, @RequestBody Map<String, String> body) {
        User user = currentUser(session);
        String question = body == null ? null : body.get("question");
        if (question == null || question.isBlank()) {
            throw new IllegalArgumentException("问题不能为空");
        }
        return retrievalService.retrieve(user.getId(), question.trim(), TOP_K, 0.0)
                .stream()
                .map(hit -> Map.<String, Object>of(
                        "snippetId", hit.snippet().getId(),
                        "title", hit.snippet().getTitle(),
                        "language", hit.snippet().getLanguage(),
                        "score", Math.round(hit.score() * 10000d) / 10000d))
                .toList();
    }
}
