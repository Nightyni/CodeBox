package com.codebox.controller;

import com.codebox.dto.SnippetRequest;
import com.codebox.dto.SnippetResponse;
import com.codebox.entity.Snippet;
import com.codebox.entity.User;
import com.codebox.service.AuthService;
import com.codebox.service.SnippetService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Verifies the HTTP contract of the snippet API: authentication is required,
 * deletion is not reachable via GET, and input is validated.
 */
class SnippetControllerTest {

    private static final String SESSION_USER_ID = "userId";

    private SnippetService snippetService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        AuthService authService = mock(AuthService.class);
        snippetService = mock(SnippetService.class);

        User user = new User();
        user.setId(1L);
        user.setUsername("alice");
        when(authService.findById(anyLong())).thenReturn(user);

        mockMvc = MockMvcBuilders
                .standaloneSetup(new SnippetController(authService, snippetService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    private SnippetResponse sample() {
        return new SnippetResponse(1L, "分页查询", "SELECT 1", "SQL", "分页,Sql",
                "分页查询示例", 0, LocalDateTime.now());
    }

    @Test
    @DisplayName("GET /api/snippets without a session is 401, not an empty list")
    void listRequiresAuthentication() throws Exception {
        mockMvc.perform(get("/api/snippets"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void listReturnsPageShape() throws Exception {
        Snippet entity = new Snippet();
        entity.setId(1L);
        entity.setTitle("分页查询");
        entity.setContent("SELECT 1");
        entity.setLanguage("SQL");
        entity.setTags("分页,Sql");
        when(snippetService.search(eq(1L), any(), any(), any(), any(), any()))
                .thenReturn(com.codebox.dto.PageResponse.of(List.of(entity), 1, 10, 1));

        mockMvc.perform(get("/api/snippets").sessionAttr(SESSION_USER_ID, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalCount").value(1))
                .andExpect(jsonPath("$.items[0].title").value("分页查询"))
                .andExpect(jsonPath("$.items[0].tags").value("分页,Sql"));
    }

    @Test
    @DisplayName("DELETE removes a snippet")
    void deleteWorks() throws Exception {
        when(snippetService.delete(1L, 5L)).thenReturn(true);

        mockMvc.perform(delete("/api/snippets/5").sessionAttr(SESSION_USER_ID, 1L))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    @DisplayName("GET /api/snippets/{id} is NOT a delete route (state change over GET is forbidden)")
    void deleteIsNotReachableByGet() throws Exception {
        mockMvc.perform(get("/api/snippets/5").sessionAttr(SESSION_USER_ID, 1L))
                .andExpect(status().isNotFound());

        verify(snippetService, never()).delete(anyLong(), anyLong());
    }

    @Test
    void deletingSomeoneElsesSnippetIs404() throws Exception {
        when(snippetService.delete(1L, 99L)).thenReturn(false);

        mockMvc.perform(delete("/api/snippets/99").sessionAttr(SESSION_USER_ID, 1L))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("blank title is rejected by bean validation with a field-level message")
    void createValidatesInput() throws Exception {
        String body = objectMapper.writeValueAsString(
                new SnippetRequestFixture("", "SELECT 1", "SQL").toMap());

        mockMvc.perform(post("/api/snippets")
                        .sessionAttr(SESSION_USER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.title").exists());

        verify(snippetService, never()).create(anyLong(), any());
    }

    @Test
    void createReturns201() throws Exception {
        Snippet created = new Snippet();
        created.setId(1L);
        created.setTitle("分页查询");
        created.setContent("SELECT 1");
        created.setLanguage("SQL");
        when(snippetService.create(eq(1L), any())).thenReturn(created);

        String body = objectMapper.writeValueAsString(
                new SnippetRequestFixture("分页查询", "SELECT 1", "SQL").toMap());

        mockMvc.perform(post("/api/snippets")
                        .sessionAttr(SESSION_USER_ID, 1L)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("分页查询"));
    }

    /** Small helper: builds the JSON body as a map so we control exactly which fields are present. */
    private record SnippetRequestFixture(String title, String content, String language) {
        java.util.Map<String, Object> toMap() {
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("title", title);
            m.put("content", content);
            m.put("language", language);
            return m;
        }
    }
}
