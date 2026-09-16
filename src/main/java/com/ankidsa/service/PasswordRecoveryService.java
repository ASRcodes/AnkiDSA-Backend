package com.ankidsa.service;

import com.ankidsa.config.PasswordRules;
import com.ankidsa.entity.PasswordResetToken;
import com.ankidsa.repository.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.*;
import org.springframework.core.task.TaskExecutor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.server.ResponseStatusException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.*;
import java.time.*;
import java.util.*;
import java.util.concurrent.RejectedExecutionException;

@Service @Slf4j
public class PasswordRecoveryService {
    private final UserRepository users;
    private final PasswordResetTokenRepository tokens;
    private final ReminderDeviceRepository devices;
    private final PasswordEncoder passwords;
    private final ObjectProvider<AccountMailSender> mail;
    private final TaskExecutor executor;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final boolean enabled;
    private final String appUrl;
    private final SecureRandom random = new SecureRandom();

    public PasswordRecoveryService(UserRepository users, PasswordResetTokenRepository tokens,
            ReminderDeviceRepository devices, PasswordEncoder passwords, ObjectProvider<AccountMailSender> mail,
            @Qualifier("accountMailExecutor") TaskExecutor executor,
            PlatformTransactionManager transactionManager, Clock clock,
            @Value("${app.recovery.enabled:false}") boolean enabled,
            @Value("${app.public-url:http://localhost:3000}") String appUrl) {
        this.users = users;
        this.tokens = tokens;
        this.devices = devices;
        this.passwords = passwords;
        this.mail = mail;
        this.executor = executor;
        this.transactions = new TransactionTemplate(transactionManager);
        this.clock = clock;
        this.enabled = enabled;
        this.appUrl = appUrl.replaceAll("/+$", "");
        if (enabled) {
            URI uri = URI.create(this.appUrl);
            boolean local = "localhost".equals(uri.getHost()) || "127.0.0.1".equals(uri.getHost());
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getFragment() != null || !("https".equals(uri.getScheme())
                    || local && "http".equals(uri.getScheme())))
                throw new IllegalStateException("APP_PUBLIC_URL must be an HTTPS app URL or a local demo URL.");
        }
    }

    public boolean isEnabled() { return enabled; }

    public void request(String email) {
        if (!enabled)
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Password recovery is not available yet.");
        // Neither the HTTP response nor its timing depends on whether this account exists.
        try {
            executor.execute(() -> issue(email.trim().toLowerCase(Locale.ROOT)));
        } catch (RejectedExecutionException e) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS, "Please wait a moment before trying again.");
        }
    }

    private record Issued(String email, String token) {}

    private void issue(String email) {
        try {
            Issued issued = transactions.execute(status -> {
                var user = users.lockByEmail(email).orElse(null);
                Instant now = clock.instant();
                if (user == null || user.getLastResetRequestAt() != null
                        && user.getLastResetRequestAt().plusSeconds(60).isAfter(now)) return null;
                byte[] bytes = new byte[32];
                random.nextBytes(bytes);
                String token = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
                tokens.deleteExpiredForUser(user.getId(), now);
                tokens.save(PasswordResetToken.builder().tokenHash(fingerprint(token)).user(user)
                    .expiresAt(now.plus(Duration.ofMinutes(30))).build());
                user.setLastResetRequestAt(now);
                users.save(user);
                return new Issued(user.getEmail(), token);
            });
            if (issued != null) {
                String link = appUrl + "/#/reset-password?token=" + issued.token();
                mail.getObject().send(issued.email(), "Reset your AnkiDSA password",
                    "A fresh start for your account.\n\nChoose a new password using this link:\n"
                    + link + "\n\nThis link expires in 30 minutes and can be used once. "
                    + "If you did not request it, you can ignore this email.\n\nAnkiDSA");
            }
        } catch (Exception e) {
            log.warn("Recovery email could not be prepared or delivered: {}", e.getClass().getSimpleName());
        }
    }

    public void reset(String token, String password) {
        if (!enabled || token == null || !token.matches("[A-Za-z0-9_-]{43}")) throw invalidLink();
        PasswordRules.validate(password);
        String hash = fingerprint(token);
        String recipient = transactions.execute(status -> {
            var candidate = tokens.findById(hash).orElseThrow(PasswordRecoveryService::invalidLink);
            var user = users.lockById(candidate.getUser().getId()).orElseThrow(PasswordRecoveryService::invalidLink);
            // Recheck in the database after taking the user lock; another reset may have consumed it.
            if (!tokens.existsById(hash) || !candidate.getExpiresAt().isAfter(clock.instant()))
                throw invalidLink();
            user.setPasswordHash(passwords.encode(password));
            user.setTokenVersion(user.getTokenVersion() + 1);
            user.setFcmToken(null);
            users.save(user);
            tokens.deleteForUser(user.getId());
            devices.removeForUser(user.getId());
            return user.getEmail();
        });
        try {
            executor.execute(() -> {
                try {
                    mail.getObject().send(recipient, "Your AnkiDSA password has changed",
                        "Your password was changed successfully. Previous sessions have been signed out.\n\n"
                        + "If you did not make this change, use account recovery immediately at "
                        + appUrl + ".\n\nAnkiDSA");
                } catch (Exception e) {
                    log.warn("Password-change notification could not be delivered: {}", e.getClass().getSimpleName());
                }
            });
        } catch (RejectedExecutionException e) {
            log.warn("Password-change notification queue is full.");
        }
    }

    public static String fingerprint(String token) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                .digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException invalidLink() {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST,
            "This recovery link is invalid or has expired. Request a new one.");
    }
}
