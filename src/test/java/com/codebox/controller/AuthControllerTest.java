package com.codebox.controller;

import com.codebox.entity.User;
import com.codebox.service.AuthService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        mockMvc = MockMvcBuilders
                .standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .build();
    }

    @Test
    void loginSucceedsAndEstablishesSession() throws Exception {
        User user = new User();
        user.setId(7L);
        user.setUsername("alice");
        user.setEmail("a@b.com");
        when(authService.authenticate(any())).thenReturn(user);

        String body = objectMapper.writeValueAsString(Map.of("username", "alice", "password", "supersecret"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(7))
                .andExpect(jsonPath("$.username").value("alice"))
                .andExpect(request().sessionAttribute("userId", 7L));
    }

    @Test
    void loginWithBadCredentialsIs401() throws Exception {
        when(authService.authenticate(any())).thenReturn(null);

        String body = objectMapper.writeValueAsString(Map.of("username", "alice", "password", "wrong"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("用户名或密码错误"));
    }

    @Test
    @DisplayName("a missing password field is a validation error, not a 500")
    void loginValidatesBody() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of("username", "alice"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists());
    }

    @Test
    @DisplayName("short password and bad email are rejected at registration")
    void registerValidatesInput() throws Exception {
        String body = objectMapper.writeValueAsString(Map.of(
                "username", "alice", "password", "123", "email", "not-an-email"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fields.password").exists())
                .andExpect(jsonPath("$.fields.email").exists());
    }

    @Test
    void duplicateUsernameIs409() throws Exception {
        when(authService.register(any())).thenReturn(null);

        String body = objectMapper.writeValueAsString(Map.of(
                "username", "alice", "password", "supersecret", "email", "a@b.com"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isConflict());
    }

    @Test
    void meRequiresLogin() throws Exception {
        mockMvc.perform(get("/api/auth/me")).andExpect(status().isUnauthorized());
    }
}
