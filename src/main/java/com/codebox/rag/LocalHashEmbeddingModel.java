package com.codebox.rag;

/**
 * Deterministic local vectorizer (hashing / "hashing trick").
 *
 * It is NOT a semantic model - it captures lexical overlap via character bigrams and
 * token hashing, which is enough for code search to work in a demo without any API key.
 * Configure a real embedding endpoint to get true semantic retrieval.
 */
public class LocalHashEmbeddingModel implements EmbeddingModel {

    public static final int DIMENSIONS = 512;

    @Override
    public float[] embed(String text) {
        float[] v = new float[DIMENSIONS];
        if (text == null || text.isBlank()) return v;

        String normalized = text.toLowerCase();
        // token-level features
        for (String token : normalized.split("[^\\p{Alnum}_]+")) {
            if (token.isBlank()) continue;
            addFeature(v, token, 1.0f);
        }
        // character bigrams keep partial matches (e.g. "分页查询" vs "分页") alive
        String compact = normalized.replaceAll("\\s+", "");
        for (int i = 0; i + 2 <= compact.length(); i++) {
            addFeature(v, compact.substring(i, i + 2), 0.5f);
        }

        // L2 normalise so cosine similarity is a plain dot product
        double norm = 0;
        for (float x : v) norm += (double) x * x;
        norm = Math.sqrt(norm);
        if (norm > 0) {
            for (int i = 0; i < v.length; i++) v[i] = (float) (v[i] / norm);
        }
        return v;
    }

    private void addFeature(float[] v, String feature, float weight) {
        int h = feature.hashCode();
        // signed hashing reduces collision bias
        int idx = Math.floorMod(h, DIMENSIONS);
        float sign = ((h >>> 16) & 1) == 0 ? 1f : -1f;
        v[idx] += sign * weight;
    }

    @Override
    public String name() {
        return "local-hash-512";
    }

    @Override
    public int dimensions() {
        return DIMENSIONS;
    }
}
