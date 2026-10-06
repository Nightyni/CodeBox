package com.codebox.rag;

/**
 * Turns text into a vector. Pluggable so the demo works out of the box
 * (local vectorizer) and improves automatically when a real embedding API is configured.
 */
public interface EmbeddingModel {

    float[] embed(String text);

    String name();

    int dimensions();
}
