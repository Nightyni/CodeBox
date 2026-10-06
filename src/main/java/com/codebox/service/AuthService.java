package com.codebox.service;

import com.codebox.dto.LoginRequest;
import com.codebox.dto.RegisterRequest;
import com.codebox.entity.User;
import com.codebox.mapper.UserMapper;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

@Service
public class AuthService {

    private final UserMapper userMapper;
    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public AuthService(UserMapper userMapper) {
        this.userMapper = userMapper;
    }

    /** @return the user on success, null when the username is unknown or the password is wrong. */
    public User authenticate(LoginRequest request) {
        User user = userMapper.findByUsername(request.getUsername());
        if (user == null) return null;
        // BCrypt verification is constant-time with respect to hash comparison.
        return passwordEncoder.matches(request.getPassword(), user.getPassword()) ? user : null;
    }

    /** @return the new user id, or null when the username is taken. */
    public Long register(RegisterRequest request) {
        if (userMapper.countByUsername(request.getUsername()) > 0) {
            return null;
        }
        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setEmail(request.getEmail().trim().toLowerCase());
        userMapper.insert(user);
        return user.getId();
    }

    public User findById(Long id) {
        return userMapper.findById(id);
    }
}
