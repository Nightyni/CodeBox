package com.codebox.rag;

import com.codebox.entity.Snippet;
import com.codebox.llm.ChatModel;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class SnippetEnricherTest {

    /** Records the prompt and returns a canned reply. */
    static class FakeChatModel implements ChatModel {
        String reply;
        String lastSystem;
        String lastUser;
        boolean available = true;

        FakeChatModel(String reply) { this.reply = reply; }

        @Override
        public String complete(String systemPrompt, String userPrompt) {
            this.lastSystem = systemPrompt;
            this.lastUser = userPrompt;
            return reply;
        }

        @Override
        public boolean available() { return available; }
    }

    private Snippet snippet(String tags) {
        Snippet s = new Snippet();
        s.setTitle("MyBatis 分页查询");
        s.setLanguage("Java");
        s.setContent("SELECT * FROM t LIMIT 10");
        s.setTags(tags);
        return s;
    }

    @Test
    @DisplayName("parses a plain JSON reply and fills tags + summary")
    void fillsFromPlainJson() {
        var model = new FakeChatModel("{\"tags\": \"MyBatis,分页\", \"summary\": \"分页查询示例\"}");
        var enricher = new SnippetEnricher(model, new ObjectMapper());

        Snippet s = snippet(null);
        enricher.enrich(s);

        assertThat(s.getTags()).isEqualTo("MyBatis,分页");
        assertThat(s.getSummary()).isEqualTo("分页查询示例");
    }

    @Test
    @DisplayName("tolerates the ```json fences models habitually add")
    void toleratesMarkdownFences() {
        var model = new FakeChatModel("```json\n{\"tags\": \"a,b\", \"summary\": \"s\"}\n```");
        var enricher = new SnippetEnricher(model, new ObjectMapper());

        Snippet s = snippet(null);
        enricher.enrich(s);

        assertThat(s.getTags()).isEqualTo("a,b");
    }

    @Test
    @DisplayName("never overwrites tags the user typed by hand")
    void doesNotClobberUserTags() {
        var model = new FakeChatModel("{\"tags\": \"llm,generated\", \"summary\": \"llm summary\"}");
        var enricher = new SnippetEnricher(model, new ObjectMapper());

        Snippet s = snippet("我自己的标签");
        enricher.enrich(s);

        assertThat(s.getTags()).isEqualTo("我自己的标签");
        assertThat(s.getSummary()).isEqualTo("llm summary");
    }

    @Test
    @DisplayName("malformed model output leaves the snippet untouched instead of failing the save")
    void malformedJsonIsIgnored() {
        var model = new FakeChatModel("I cannot help with that.");
        var enricher = new SnippetEnricher(model, new ObjectMapper());

        Snippet s = snippet(null);
        enricher.enrich(s);

        assertThat(s.getTags()).isNull();
        assertThat(s.getSummary()).isNull();
    }

    @Test
    void unavailableModelIsSkippedEntirely() {
        var model = new FakeChatModel("{\"tags\":\"x\",\"summary\":\"y\"}");
        model.available = false;
        var enricher = new SnippetEnricher(model, new ObjectMapper());

        Snippet s = snippet(null);
        enricher.enrich(s);

        assertThat(s.getTags()).isNull();
        assertThat(model.lastUser).isNull();
    }
}
