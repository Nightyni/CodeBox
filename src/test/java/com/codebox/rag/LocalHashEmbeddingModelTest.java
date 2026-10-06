package com.codebox.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class LocalHashEmbeddingModelTest {

    private final LocalHashEmbeddingModel model = new LocalHashEmbeddingModel();

    @Test
    @DisplayName("same text always produces the same vector (deterministic)")
    void deterministic() {
        assertThat(model.embed("MyBatis 分页查询"))
                .containsExactly(model.embed("MyBatis 分页查询"));
    }

    @Test
    void vectorIsL2Normalised() {
        float[] v = model.embed("Spring Boot 定时任务 @Scheduled");
        double norm = 0;
        for (float x : v) norm += (double) x * x;
        assertThat(Math.sqrt(norm)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-4));
    }

    @Test
    @DisplayName("related text scores higher than unrelated text")
    void similarTextScoresHigher() {
        var math = new VectorMath();
        float[] query = model.embed("分页查询");
        float[] related = model.embed("MyBatis Plus 分页查询");
        float[] unrelated = model.embed("CSS 动画 fadeIn");

        double relatedScore = math.cosine(query, related);
        double unrelatedScore = math.cosine(query, unrelated);

        assertThat(relatedScore).isGreaterThan(unrelatedScore);
    }

    @Test
    void blankInputProducesZeroVector() {
        float[] v = model.embed("   ");
        for (float x : v) assertThat(x).isZero();
    }

    @Test
    void dimensionsMatchDeclaredValue() {
        assertThat(model.embed("anything")).hasSize(model.dimensions());
    }
}
