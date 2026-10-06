package com.codebox.rag;

import com.codebox.entity.Snippet;
import com.codebox.entity.SnippetEmbedding;
import com.codebox.mapper.SnippetEmbeddingMapper;
import com.codebox.mapper.SnippetMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vector index over a user's snippets.
 *
 * Vectors are persisted in {@code snippet_embedding} and held in an in-memory cache,
 * so a query is a cosine scan instead of N model calls. This is deliberately simple:
 * the corpus is a personal snippet library (hundreds to low thousands), where a full
 * scan is faster than the overhead of an external vector database.
 */
@Service
public class RetrievalService {

    private static final Logger log = LoggerFactory.getLogger(RetrievalService.class);

    private final SnippetMapper snippetMapper;
    private final SnippetEmbeddingMapper embeddingMapper;
    private final EmbeddingModel embeddingModel;
    private final VectorMath vectorMath;

    private final Map<Long, float[]> cache = new HashMap<>();
    private volatile boolean cacheLoaded = false;

    public RetrievalService(SnippetMapper snippetMapper,
                            SnippetEmbeddingMapper embeddingMapper,
                            EmbeddingModel embeddingModel,
                            VectorMath vectorMath) {
        this.snippetMapper = snippetMapper;
        this.embeddingMapper = embeddingMapper;
        this.embeddingModel = embeddingModel;
        this.vectorMath = vectorMath;
    }

    public record Scored(Snippet snippet, double score) {}

    /** Best-effort: the snippet is still saved if embedding fails. */
    @Transactional
    public void index(Long userId, Snippet snippet) {
        try {
            String text = documentText(snippet);
            float[] vector = embeddingModel.embed(text);

            // Replace any previous vector for this snippet (portable upsert).
            embeddingMapper.deleteBySnippetId(snippet.getId());

            SnippetEmbedding record = new SnippetEmbedding();
            record.setSnippetId(snippet.getId());
            record.setUserId(userId);
            record.setModel(embeddingModel.name());
            record.setVector(vectorMath.serialize(vector));
            record.setDimensions(vector.length);
            embeddingMapper.upsert(record);

            cache.put(snippet.getId(), vector);
        } catch (Exception e) {
            log.warn("Failed to index snippet {}: {}", snippet.getId(), e.toString());
        }
    }

    /**
     * Recomputes embeddings for every snippet the user owns.
     *
     * Needed for rows that entered the database outside the API (seed scripts, bulk
     * import): those have no vector, so without a rebuild they are invisible to
     * retrieval until someone edits them one by one.
     *
     * @return how many snippets were indexed
     */
    public int reindexAll(Long userId) {
        List<Snippet> snippets = snippetMapper.findAllByUser(userId);
        int indexed = 0;
        for (Snippet snippet : snippets) {
            index(userId, snippet);
            indexed++;
        }
        invalidateCache();
        ensureLoaded(userId);
        log.info("Reindexed {} snippets for user {}", indexed, userId);
        return indexed;
    }

    /** Name of the vectorizer actually in use (real API model or the local fallback). */
    public String embeddingModelName() {
        return embeddingModel.name();
    }

    /** Number of vectors currently held for a user, for diagnostics. */
    public int indexedCount(Long userId) {
        ensureLoaded(userId);
        return cache.size();
    }

    public void remove(Long snippetId) {
        try {
            embeddingMapper.deleteBySnippetId(snippetId);
        } catch (Exception e) {
            log.warn("Failed to delete embedding for snippet {}: {}", snippetId, e.toString());
        }
        cache.remove(snippetId);
    }

    /** Top-K snippets by cosine similarity to the query. */
    public List<Scored> retrieve(Long userId, String query, int topK, double minScore) {
        float[] queryVector = embeddingModel.embed(query);
        ensureLoaded(userId);

        Map<Long, Snippet> snippets = new HashMap<>();
        for (Snippet s : snippetMapper.findAllByUser(userId)) snippets.put(s.getId(), s);

        List<Scored> scored = new ArrayList<>();
        for (Map.Entry<Long, float[]> entry : cache.entrySet()) {
            Snippet snippet = snippets.get(entry.getKey());
            if (snippet == null) continue;
            double score = vectorMath.cosine(queryVector, entry.getValue());
            if (score >= minScore) scored.add(new Scored(snippet, score));
        }
        scored.sort(Comparator.comparingDouble(Scored::score).reversed());
        return scored.size() > topK ? scored.subList(0, topK) : scored;
    }

    private synchronized void ensureLoaded(Long userId) {
        if (cacheLoaded) return;
        try {
            List<SnippetEmbedding> stored = embeddingMapper.findByUser(userId);
            for (SnippetEmbedding e : stored) {
                cache.put(e.getSnippetId(), vectorMath.deserialize(e.getVector()));
            }
            cacheLoaded = true;
            log.info("Loaded {} embeddings into the retrieval cache", cache.size());
        } catch (Exception e) {
            log.warn("Could not load embeddings (will embed on demand): {}", e.toString());
        }
    }

    public synchronized void invalidateCache() {
        cache.clear();
        cacheLoaded = false;
    }

    /** Text actually embedded for a snippet: title + summary + tags + code. */
    static String documentText(Snippet s) {
        StringBuilder sb = new StringBuilder();
        if (s.getTitle() != null) sb.append(s.getTitle()).append('\n');
        if (s.getSummary() != null) sb.append(s.getSummary()).append('\n');
        if (s.getTags() != null) sb.append(s.getTags()).append('\n');
        if (s.getLanguage() != null) sb.append(s.getLanguage()).append('\n');
        if (s.getContent() != null) sb.append(s.getContent());
        return sb.toString();
    }
}
