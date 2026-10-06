package com.codebox.rag;

import com.codebox.entity.Snippet;
import com.codebox.entity.User;
import com.codebox.mapper.SnippetMapper;
import com.codebox.mapper.UserMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Builds vector indexes that are missing at startup.
 *
 * Rows that arrive via seed scripts or bulk import never pass through the API, so
 * they have no embedding. Without this, natural-language search silently returns
 * nothing for them, which looks like a broken feature rather than missing data.
 */
@Component
public class VectorIndexBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(VectorIndexBootstrap.class);

    private final SnippetMapper snippetMapper;
    private final UserMapper userMapper;
    private final RetrievalService retrievalService;

    public VectorIndexBootstrap(SnippetMapper snippetMapper,
                               UserMapper userMapper,
                               RetrievalService retrievalService) {
        this.snippetMapper = snippetMapper;
        this.userMapper = userMapper;
        this.retrievalService = retrievalService;
    }

    @Override
    public void run(ApplicationArguments args) {
        try {
            for (Long userId : userMapper.findAllIds()) {
                reindexIfIncomplete(userId);
            }
        } catch (Exception e) {
            // Never block startup on the index: the API-level /api/ask/reindex still works.
            log.warn("Startup vector indexing skipped: {}", e.toString());
        }
    }

    private void reindexIfIncomplete(Long userId) {
        List<Snippet> snippets = snippetMapper.findAllByUser(userId);
        if (snippets.isEmpty()) return;

        int indexed = retrievalService.indexedCount(userId);
        if (indexed >= snippets.size()) {
            log.debug("Vector index already complete for user {} ({} vectors)", userId, indexed);
            return;
        }
        log.info("Vector index incomplete for user {} ({}/{}); rebuilding",
                userId, indexed, snippets.size());
        retrievalService.reindexAll(userId);
    }
}