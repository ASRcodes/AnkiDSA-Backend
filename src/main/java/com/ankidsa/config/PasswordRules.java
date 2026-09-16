package com.ankidsa.config;

import java.nio.charset.StandardCharsets;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class PasswordRules {
    private PasswordRules() {}
    public static void validate(String password) {
        if (password == null || password.isBlank() || password.length() < 10 || password.length() > 64)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use a password with 10–64 characters.");
        if (password.getBytes(StandardCharsets.UTF_8).length > 72)
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "This password is too long. Use fewer characters, especially symbols or emoji.");
    }
}
