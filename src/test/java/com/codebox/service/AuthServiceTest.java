package com.codebox.service;

import com.codebox.dto.LoginRequest;
import com.codebox.dto.RegisterRequest;
import com.codebox.entity.User;
import com.codebox.mapper.UserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

class AuthServiceTest {

    private final UserMapper userMapper = mock(UserMapper.class);
    private final AuthService authService = new AuthService(userMapper);
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    @Test
    @DisplayName("registration stores a BCrypt hash, never the plaintext password")
    void registerHashesPassword() {
        when(userMapper.countByUsername(anyString())).thenReturn(0);

        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setPassword("supersecret");
        req.setEmail("Alice@Example.COM");

        authService.register(req);

        ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
        verify(userMapper).insert(captor.capture());
        User saved = captor.getValue();

        assertThat(saved.getPassword()).isNotEqualTo("supersecret");
        assertThat(saved.getPassword()).startsWith("$2a$");   // BCrypt marker
        assertThat(encoder.matches("supersecret", saved.getPassword())).isTrue();
        assertThat(saved.getEmail()).isEqualTo("alice@example.com"); // normalised
    }

    @Test
    void registerRejectsDuplicateUsername() {
        when(userMapper.countByUsername("alice")).thenReturn(1);

        RegisterRequest req = new RegisterRequest();
        req.setUsername("alice");
        req.setPassword("supersecret");
        req.setEmail("a@b.com");

        assertThat(authService.register(req)).isNull();
        verify(userMapper, never()).insert(any());
    }

    @Test
    @DisplayName("login succeeds with the correct password against a BCrypt hash")
    void loginAcceptsCorrectPassword() {
        User stored = new User();
        stored.setId(1L);
        stored.setUsername("alice");
        stored.setPassword(encoder.encode("supersecret"));
        when(userMapper.findByUsername("alice")).thenReturn(stored);

        LoginRequest req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("supersecret");

        assertThat(authService.authenticate(req)).isNotNull();
    }

    @Test
    void loginRejectsWrongPassword() {
        User stored = new User();
        stored.setId(1L);
        stored.setPassword(encoder.encode("supersecret"));
        when(userMapper.findByUsername("alice")).thenReturn(stored);

        LoginRequest req = new LoginRequest();
        req.setUsername("alice");
        req.setPassword("wrong-password");

        assertThat(authService.authenticate(req)).isNull();
    }

    @Test
    void loginRejectsUnknownUser() {
        when(userMapper.findByUsername("ghost")).thenReturn(null);

        LoginRequest req = new LoginRequest();
        req.setUsername("ghost");
        req.setPassword("whatever");

        assertThat(authService.authenticate(req)).isNull();
    }
}
