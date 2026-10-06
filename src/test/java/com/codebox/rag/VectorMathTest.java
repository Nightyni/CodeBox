package com.codebox.rag;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class VectorMathTest {

    private final VectorMath math = new VectorMath();

    @Test
    @DisplayName("serialize/deserialize round-trips a vector")
    void roundTrip() {
        float[] original = {0.5f, -0.25f, 1f, 0f};
        float[] restored = math.deserialize(math.serialize(original));
        assertThat(restored).containsExactly(original);
    }

    @Test
    @DisplayName("deserialize tolerates a malformed component instead of throwing")
    void deserializeSkipsGarbage() {
        assertThat(math.deserialize("0.5,not-a-number,0.25")).containsExactly(0.5f, 0.25f);
    }

    @Test
    void deserializeOfBlankIsEmpty() {
        assertThat(math.deserialize("")).isEmpty();
        assertThat(math.deserialize(null)).isEmpty();
    }

    @Test
    @DisplayName("cosine is 1 for identical vectors and 0 for orthogonal ones")
    void cosineBasics() {
        float[] a = {1f, 0f};
        float[] b = {0f, 1f};
        assertThat(math.cosine(a, a)).isCloseTo(1.0, org.assertj.core.data.Offset.offset(1e-6));
        assertThat(math.cosine(a, b)).isCloseTo(0.0, org.assertj.core.data.Offset.offset(1e-6));
    }

    @Test
    @DisplayName("cosine returns 0 on mismatched dimensions rather than throwing")
    void cosineDimensionMismatch() {
        assertThat(math.cosine(new float[]{1f}, new float[]{1f, 2f})).isZero();
    }
}
