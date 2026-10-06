package com.codebox.controller;

import com.codebox.dto.LoginRequest;
import com.codebox.dto.RegisterRequest;
import com.codebox.entity.User;
import com.codebox.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController extends BaseController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        super(authService);
        this.authService = authService;
    }

    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Object> register(@Valid @RequestBody RegisterRequest request) {
        Long id = authService.register(request);
        if (id == null) {
            throw ApiException.conflict("用户名已存在");
        }
        return Map.of("id", id, "username", request.getUsername());
    }

    @PostMapping("/login")
    public Map<String, Object> login(@Valid @RequestBody LoginRequest request,
                                     HttpServletRequest httpRequest) {
        User user = authService.authenticate(request);
        if (user == null) {
            throw ApiException.unauthorized("用户名或密码错误");
        }
        return establishSession(user, httpRequest);
    }

    /**
     * Session-fixation defence: invalidate any session that existed before login,
     * then bind the identity to a brand-new session.
     */
    private Map<String, Object> establishSession(User user, HttpServletRequest httpRequest) {
        HttpSession existing = httpRequest.getSession(false);
        if (existing != null) {
            existing.invalidate();
        }
        HttpSession session = httpRequest.getSession(true);
        session.setAttribute(SESSION_USER_ID, user.getId());
        session.setMaxInactiveInterval(60 * 60);

        return Map.of("id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail() == null ? "" : user.getEmail());
    }

    @GetMapping("/me")
    public Map<String, Object> me(HttpSession session) {
        User user = currentUser(session);
        return Map.of("id", user.getId(),
                "username", user.getUsername(),
                "email", user.getEmail() == null ? "" : user.getEmail());
    }

    @PostMapping("/logout")
    public Map<String, Object> logout(HttpSession session) {
        session.invalidate();
        return Map.of("success", true);
    }
}
