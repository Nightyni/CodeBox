package com.codebox.rag;

import com.codebox.dto.AskResponse;
import com.codebox.entity.Snippet;
import com.codebox.llm.ChatModel;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class AnswerGeneratorTest {

    static class StubChatModel implements ChatModel {
        final String reply;
        final boolean available;
        String lastUser;

        StubChatModel(String reply, boolean available) {
            this.reply = reply;
            this.available = available;
        }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            this.lastUser = userPrompt;
            return reply;
        }

        @Override
        public boolean available() { return available; }

        boolean failed = false;

        @Override
        public boolean lastCallFailed() { return failed; }
    }

    private RetrievalService.Scored hit(long id, String title, double score) {
        Snippet s = new Snippet();
        s.setId(id);
        s.setTitle(title);
        s.setLanguage("Java");
        s.setContent("code");
        return new RetrievalService.Scored(s, score);
    }

    @Test
    @DisplayName("no hits => refuses to answer and flags insufficient evidence")
    void noHitsMeansNoAnswer() {
        var gen = new AnswerGenerator(new StubChatModel("should not be called", true));
        AskResponse res = gen.answer("怎么分页?", List.of());

        assertThat(res.insufficientEvidence()).isTrue();
        assertThat(res.citations()).isEmpty();
    }

    @Test
    @DisplayName("model signalling INSUFFICIENT_EVIDENCE is reported, not passed through")
    void insufficientMarkerIsDetected() {
        var gen = new AnswerGenerator(new StubChatModel("INSUFFICIENT_EVIDENCE", true));
        AskResponse res = gen.answer("怎么用 Redis 做分布式锁?", List.of(hit(1, "分页查询", 0.9)));

        assertThat(res.insufficientEvidence()).isTrue();
        assertThat(res.answer()).doesNotContain(AnswerGenerator.INSUFFICIENT_MARKER);
    }

    @Test
    @DisplayName("retrieved context is injected into the prompt with numbered citations")
    void contextIsNumberedInPrompt() {
        var model = new StubChatModel("使用 LIMIT 即可 [1]", true);
        var gen = new AnswerGenerator(model);

        AskResponse res = gen.answer("怎么分页?", List.of(hit(7, "MyBatis 分页查询", 0.87)));

        assertThat(model.lastUser).contains("[1]").contains("MyBatis 分页查询");
        assertThat(res.answer()).isEqualTo("使用 LIMIT 即可 [1]");
        assertThat(res.insufficientEvidence()).isFalse();
        assertThat(res.citations()).hasSize(1);
        assertThat(res.citations().get(0).snippetId()).isEqualTo(7L);
        assertThat(res.citations().get(0).score()).isEqualTo(0.87);
    }

    @Test
    @DisplayName("without an API key, retrieval results are still returned")
    void degradesGracefullyWithoutApiKey() {
        var gen = new AnswerGenerator(new StubChatModel("", false));
        AskResponse res = gen.answer("怎么分页?", List.of(hit(1, "分页", 0.5)));

        assertThat(res.insufficientEvidence()).isFalse();
        assertThat(res.citations()).hasSize(1);
        assertThat(res.answer()).contains("API Key").contains("application-local.yml");
    }

    @Test
    @DisplayName("a failed model call is reported as an outage, NOT as insufficient evidence")
    void failedCallIsNotReportedAsInsufficientEvidence() {
        var model = new StubChatModel("", true);
        model.failed = true;
        var gen = new AnswerGenerator(model);

        AskResponse res = gen.answer("怎么分页?", List.of(hit(1, "分页查询", 0.8)));

        assertThat(res.llmFailed()).isTrue();
        assertThat(res.insufficientEvidence()).isFalse();
        assertThat(res.answer()).contains("调用失败");
        // retrieval results are still returned so the user is not left with nothing
        assertThat(res.citations()).hasSize(1);
    }

    @Test
    void truncateKeepsLongContentBounded() {
        String long_ = "x".repeat(2000);
        String out = AnswerGenerator.truncate(long_, 100);
        assertThat(out).hasSizeLessThan(200).contains("已截断");
    }
}
