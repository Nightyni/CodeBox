package com.codebox.rag;

import com.codebox.entity.SnippetEmbedding;
import com.codebox.llm.LlmProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * OpenAI-compatible embedding client (works with SiliconFlow / DashScope / OpenAI).
 * Falls back to {@link LocalHashEmbeddingModel} when the endpoint is not configured,
 * so the app is always runnable.
 */
@Component
public class EmbeddingClient implements EmbeddingModel {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingClient.class);

    private final LlmProperties properties;
    private final RestClient.Builder builder;
    private final LocalHashEmbeddingModel fallback = new LocalHashEmbeddingModel();

    private volatile RestClient client;
    /**
     * Width of the vectors the configured provider actually returns, recorded on first
     * successful call. Zero means "not observed yet".
     */
    private volatile int observedDimensions;

    public EmbeddingClient(LlmProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.builder = builder;
    }

    @Override
    public float[] embed(String text) {
        if (!properties.hasEmbeddingEndpoint()) {
            return fallback.embed(text);
        }
        try {
            @SuppressWarnings("unchecked")
            Map<String, Object> response = client().post()
                    .uri("/embeddings")
                    .header("Authorization", "Bearer " + resolveEmbeddingKey())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("model", properties.getEmbeddingModel(), "input", text))
                    .retrieve()
                    .body(Map.class);
            float[] vector = parseEmbedding(response);
            if (vector.length > 0) {
                // Record the provider's real width so dimensions() stops reporting the
                // local fallback's width once a real service is in use.
                observedDimensions = vector.length;
                return vector;
            }
            log.warn("Embedding endpoint returned no vector; using local fallback");
            return fallback.embed(text);
        } catch (Exception e) {
            log.warn("Embedding call failed, using local fallback: {}", e.toString());
            return fallback.embed(text);
        }
    }

    @SuppressWarnings("unchecked")
    private float[] parseEmbedding(Map<String, Object> response) {
        if (response == null) return new float[0];
        Object data = response.get("data");
        if (!(data instanceof List<?> list) || list.isEmpty()) return new float[0];
        Object first = list.get(0);
        if (!(first instanceof Map<?, ?> item)) return new float[0];
        Object embedding = item.get("embedding");
        if (!(embedding instanceof List<?> values)) return new float[0];
        float[] out = new float[values.size()];
        for (int i = 0; i < values.size(); i++) {
            Object o = values.get(i);
            out[i] = o instanceof Number n ? n.floatValue() : 0f;
        }
        return out;
    }

    private String resolveEmbeddingKey() {
        String k = properties.getEmbeddingApiKey();
        return (k == null || k.isBlank()) ? properties.getApiKey() : k;
    }

    private RestClient client() {
        RestClient local = client;
        if (local == null) {
            synchronized (this) {
                local = client;
                if (local == null) {
                    local = builder.baseUrl(properties.getEmbeddingBaseUrl()).build();
                    client = local;
                }
            }
        }
        return local;
    }

    @Override
    public String name() {
        return properties.hasEmbeddingEndpoint()
                ? properties.getEmbeddingModel()
                : fallback.name();
    }

    /**
     * Actual vector width, learned from the first successful call.
     *
     * This used to return the local fallback's width unconditionally, which lied as soon
     * as a real embedding service was configured (e.g. 1024 vs 512). Returning a wrong
     * number is worse than returning none: the retrieval layer relies on it to spot
     * vectors produced by a different model, which would otherwise be silently
     * incomparable and make search return junk.
     */
    @Override
    public int dimensions() {
        if (!properties.hasEmbeddingEndpoint()) {
            return fallback.dimensions();
        }
        int observed = observedDimensions;
        return observed > 0 ? observed : fallback.dimensions();
    }

    /** @return the width observed from the provider so far, or 0 when unknown. */
    public int observedDimensions() {
        return observedDimensions;
    }
}

