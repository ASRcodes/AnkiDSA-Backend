package com.ankidsa.controller;

import com.ankidsa.config.*;
import com.ankidsa.entity.User;
import com.ankidsa.repository.UserRepository;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;

@RestController @RequestMapping("/api/auth") @RequiredArgsConstructor
public class AuthController {
    private final UserRepository users;
    private final JwtService jwt;
    private final PasswordEncoder passwords;
    private final com.ankidsa.service.SessionRevocations revocations;
    private final com.ankidsa.service.ReminderDeviceService devices;
    public record LogoutRequest(@Size(max = 4096) String deviceToken) {}

    @PostMapping("/logout") @ResponseStatus(HttpStatus.NO_CONTENT)
    @org.springframework.transaction.annotation.Transactional
    public void logout(jakarta.servlet.http.HttpServletRequest request,
            @Valid @RequestBody(required = false) LogoutRequest body) {
        var identity = (JwtService.Identity) request.getAttribute("ankidsa.identity");
        if (identity == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        revocations.revoke(identity);
        if (body != null && body.deviceToken() != null) devices.remove(identity.userId(), body.deviceToken());
    }

    public record Credentials(@NotBlank @Email @Size(max = 255) String email,
        @NotBlank @Size(min = 10, max = 64) String password, @Size(max = 80) String name) {}
    public record Session(String token, Long userId, String email, String name) {}

    @PostMapping("/register")
    public Session register(@Valid @RequestBody Credentials request) {
        PasswordRules.validate(request.password());
        String email = request.email().trim().toLowerCase(Locale.ROOT);
        if (users.existsByEmail(email))
            throw new ResponseStatusException(HttpStatus.CONFLICT, "An account already uses that email. Sign in instead.");
        var user = users.save(User.builder().email(email)
            .name(request.name() == null || request.name().isBlank() ? email.split("@")[0] : request.name().trim())
            .passwordHash(passwords.encode(request.password())).build());
        return session(user);
    }

    @PostMapping("/login")
    public Session login(@Valid @RequestBody Credentials request) {
        PasswordRules.validate(request.password());
        var user = users.findByEmail(request.email().trim().toLowerCase(Locale.ROOT)).orElse(null);
        String hash = user != null && user.getPasswordHash() != null ? user.getPasswordHash() : DUMMY_HASH;
        boolean matches = passwords.matches(request.password(), hash);
        if (user == null || user.getPasswordHash() == null || !matches)
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Email or password is incorrect.");
        return session(user);
    }

    @GetMapping("/verify")
    public Map<String, Object> verify() {
        var user = users.findById(AuthUtil.getCurrentUserId()).orElseThrow(() ->
            new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Account no longer exists."));
        return Map.of("valid", true, "userId", user.getId(), "email", user.getEmail(), "name", user.displayName());
    }

    private Session session(User user) {
        return new Session(jwt.generateToken(user), user.getId(), user.getEmail(), user.displayName());
    }

    private static final String DUMMY_HASH =
        new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(12).encode(UUID.randomUUID().toString());
}
