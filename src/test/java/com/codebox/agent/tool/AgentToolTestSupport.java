package com.codebox.agent.tool;

import com.codebox.agent.dto.ToolResult;
import com.codebox.entity.Snippet;
import com.codebox.llm.ChatModel;
import com.codebox.mapper.SnippetEmbeddingMapper;
import com.codebox.mapper.SnippetMapper;
import com.codebox.rag.EmbeddingModel;
import com.codebox.rag.LocalHashEmbeddingModel;
import com.codebox.rag.RetrievalService;
import com.codebox.rag.SnippetEnricher;
import com.codebox.rag.VectorMath;
import com.codebox.service.SnippetService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Shared scaffolding for tool tests.
 *
 * Tools are plain objects, so they are tested directly with mocked services instead of
 * through Spring - that keeps these tests fast and lets each one assert exactly which
 * service call should and should not happen.
 */
public class AgentToolTestSupport {

    protected static final Long USER_ID = 1L;
    protected static final ToolContext CONTEXT = new ToolContext(USER_ID);

    protected final ObjectMapper objectMapper = new ObjectMapper();
    protected final ToolJson json = new ToolJson(objectMapper);

    protected SnippetMapper snippetMapper = mock(SnippetMapper.class);
    protected SnippetEmbeddingMapper embeddingMapper = mock(SnippetEmbeddingMapper.class);
    protected RetrievalService retrievalService;
    protected SnippetService snippetService;
    protected SnippetEnricher enricher = mock(SnippetEnricher.class);

    protected AgentToolTestSupport() {
        EmbeddingModel embeddingModel = new LocalHashEmbeddingModel();
        retrievalService = new RetrievalService(snippetMapper, embeddingMapper, embeddingModel, new VectorMath());
        snippetService = new SnippetService(snippetMapper, enricher, retrievalService);
    }

    protected static ToolResult fail(String argumentsJson, AgentTool tool) {
        return tool.execute(argumentsJson, CONTEXT);
    }

    protected Snippet snippet(long id, String title, String content, String language) {
        Snippet s = new Snippet();
        s.setId(id);
        s.setUserId(USER_ID);
        s.setTitle(title);
        s.setContent(content);
        s.setLanguage(language);
        s.setTags("demo");
        s.setSummary("summary");
        s.setUseCount(0);
        return s;
    }

    /** Makes snippetService.findById return the given snippet, or null for "not owned". */
    protected void givenOwned(Snippet snippet) {
        when(snippetMapper.findById(anyLong(), anyLong())).thenReturn(snippet);
    }

    protected void givenNotOwned() {
        when(snippetMapper.findById(anyLong(), anyLong())).thenReturn(null);
    }

    protected void givenAllSnippets(Snippet... snippets) {
        when(snippetMapper.findAllByUser(anyLong())).thenReturn(java.util.List.of(snippets));
    }

    protected void givenInsertWorks() {
        when(snippetMapper.insert(any(Snippet.class))).thenAnswer(inv -> {
            Snippet s = inv.getArgument(0);
            if (s.getId() == null) s.setId(99L);
            return 1;
        });
    }

    protected void givenUpdateWorks() {
        when(snippetMapper.update(any(Snippet.class))).thenReturn(1);
    }

    protected void givenDeleteWorks(boolean ok) {
        when(snippetMapper.delete(anyLong(), anyLong())).thenReturn(ok ? 1 : 0);
    }

    protected void givenUseCountBump() {
        when(snippetMapper.incrementUseCount(anyLong(), anyLong())).thenReturn(1);
    }

    /** A chat model whose reply is scripted, for the capability tools. */
    protected ChatModel scriptedModel(String reply) {
        return new ChatModel() {
            @Override
            public String complete(String systemPrompt, String userPrompt) {
                return reply;
            }

            @Override
            public boolean available() {
                return true;
            }
        };
    }

    protected String json(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** Parses a ToolResult's text so assertions can look at fields instead of substrings. */
    protected JsonNode parse(ToolResult result) {
        try {
            return objectMapper.readTree(result.text());
        } catch (Exception e) {
            throw new IllegalStateException("Tool did not return JSON: " + result.text(), e);
        }
    }
}
