package com.codebox.controller;

import com.codebox.entity.User;
import com.codebox.service.AuthService;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.RestController;

/** Session-backed authentication. Every data endpoint resolves the user through here. */
@RestController
public abstract class BaseController {

    public static final String SESSION_USER_ID = "userId";

    private final AuthService authService;

    protected BaseController(AuthService authService) {
        this.authService = authService;
    }

    protected User currentUser(HttpSession session) {
        Object id = session.getAttribute(SESSION_USER_ID);
        if (!(id instanceof Long userId)) {
            throw ApiException.unauthorized("请先登录");
        }
        User user = authService.findById(userId);
        if (user == null) {
            session.invalidate();
            throw ApiException.unauthorized("登录已失效，请重新登录");
        }
        return user;
    }
}
