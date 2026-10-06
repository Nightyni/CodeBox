package com.codebox.controller;

import com.codebox.dto.AskResponse;
import com.codebox.entity.User;
import com.codebox.rag.AnswerGenerator;
import com.codebox.rag.LibraryQuestion;
import com.codebox.rag.RetrievalService;
import com.codebox.service.AuthService;
import com.codebox.service.StatsService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * /api/ask used to accept an unvalidated {@code Map<String,String>}, so a blank question
 * produced a 500 from the service layer instead of a 400 with a field error. These tests
 * pin the validated behaviour.
 */
class AskControllerTest {

    private static final String SESSION_USER_ID = "userId";

    private RetrievalService retrievalService;
    private StatsService statsService;
    private AnswerGenerator answerGenerator;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AuthService authService = mock(AuthService.class);
        retrievalService = mock(RetrievalService.class);
        answerGenerator = mock(AnswerGenerator.class);
        statsService = mock(StatsService.class);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(authService.findById(anyLong())).thenReturn(user);

        when(answerGenerator.answer(anyString(), any()))
                .thenReturn(new AskResponse("q", "a", List.of(), false, false, 1));

        mockMvc = MockMvcBuilders
                .standaloneSetup(new AskController(authService, retrievalService, answerGenerator, statsService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private String body(Map<String, Object> payload) throws Exception {
        return objectMapper.writeValueAsString(payload);
    }

    @Test
    @DisplayName("a missing question is a 400 with a field error, not a 500")
    void missingQuestionIsRejected() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .sessionAttr(SESSION_USER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.question").exists());
    }

    @Test
    @DisplayName("a blank question is rejected (previously it reached the service and blew up)")
    void blankQuestionIsRejected() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .sessionAttr(SESSION_USER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("question", "   "))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.question").exists());
    }

    @Test
    @DisplayName("an over-long question is rejected instead of being sent to the model")
    void overLongQuestionIsRejected() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .sessionAttr(SESSION_USER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("question", "x".repeat(2001)))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.question").exists());
    }

    @Test
    void questionIsRequiredWithoutASession() throws Exception {
        mockMvc.perform(post("/api/ask")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("question", "怎么分页"))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("a normal question still works and is trimmed")
    void validQuestionIsAnswered() throws Exception {
        when(retrievalService.retrieve(anyLong(), anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of());

        mockMvc.perform(post("/api/ask")
                        .sessionAttr(SESSION_USER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("question", "  怎么分页  "))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value("a"));

        org.mockito.Mockito.verify(answerGenerator).answer(eq("怎么分页"), any());
    }

    @Test
    @DisplayName("an overview question is answered by statistics, not by retrieval")
    void overviewQuestionBypassesRetrieval() throws Exception {
        when(statsService.summarise(anyLong())).thenReturn(
                new StatsService.LibraryStats(2,
                        new java.util.TreeMap<>(Map.of("Java", 2)),
                        List.of("分页"),
                        List.of("A", "B")));

        mockMvc.perform(post("/api/ask")
                        .sessionAttr(SESSION_USER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("question", "我的知识库有哪些内容"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answer").value(org.hamcrest.Matchers.containsString("2 条")));

        org.mockito.Mockito.verify(retrievalService, org.mockito.Mockito.never())
                .retrieve(anyLong(), anyString(), anyInt(), anyDouble());
    }

    @Test
    @DisplayName("/retrieve validates the body too")
    void retrieveValidatesBody() throws Exception {
        mockMvc.perform(post("/api/ask/retrieve")
                        .sessionAttr(SESSION_USER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.question").exists());
    }

    @Test
    void retrieveReturnsHits() throws Exception {
        var snippet = new com.codebox.entity.Snippet();
        snippet.setId(3L);
        snippet.setTitle("Redis 分布式锁");
        snippet.setLanguage("Java");
        when(retrievalService.retrieve(anyLong(), anyString(), anyInt(), anyDouble()))
                .thenReturn(List.of(new RetrievalService.Scored(snippet, 0.53624)));

        mockMvc.perform(post("/api/ask/retrieve")
                        .sessionAttr(SESSION_USER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(Map.of("question", "redis"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].snippetId").value(3))
                .andExpect(jsonPath("$[0].score").value(0.5362));
    }

    @Test
    void libraryQuestionDetectionStillCoversTheReportedPhrasings() {
        // guard: a regression here was what made "我的代码有哪些" fall through to retrieval
        org.assertj.core.api.Assertions.assertThat(LibraryQuestion.isOverview("我的代码有哪些")).isTrue();
        org.assertj.core.api.Assertions.assertThat(LibraryQuestion.isOverview("分页怎么做")).isFalse();
    }
}
