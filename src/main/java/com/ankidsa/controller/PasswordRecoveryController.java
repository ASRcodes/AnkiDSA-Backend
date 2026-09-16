package com.ankidsa.controller;

import com.ankidsa.service.PasswordRecoveryService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class PasswordRecoveryController {
    private final PasswordRecoveryService recovery;
    @org.springframework.beans.factory.annotation.Value("${app.notifications-enabled:false}")
    private boolean remindersEnabled;
    public record EmailRequest(@NotBlank @Email @Size(max = 255) String email) {}
    public record ResetRequest(@NotBlank @Pattern(regexp = "[A-Za-z0-9_-]{43}") String token,
        @NotBlank @Size(min = 10, max = 64) String password) {}

    @GetMapping("/options")
    public Map<String, Boolean> options() {
        return Map.of("passwordRecovery", recovery.isEnabled(), "dailyReminders", remindersEnabled);
    }

    @PostMapping("/forgot-password")
    public Map<String, String> request(@Valid @RequestBody EmailRequest request) {
        recovery.request(request.email());
        return Map.of("message", "If an account matches that email, you will receive a reset link. Check your inbox and spam folder.");
    }

    @PostMapping("/reset-password") @ResponseStatus(HttpStatus.NO_CONTENT)
    public void reset(@Valid @RequestBody ResetRequest request) {
        recovery.reset(request.token(), request.password());
    }
}
