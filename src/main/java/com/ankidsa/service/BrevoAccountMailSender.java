package com.ankidsa.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import tools.jackson.databind.json.JsonMapper;

/** HTTPS only in application configuration; the test constructor accepts a loopback fixture. */
@Component @Profile("!demo")
@ConditionalOnExpression("'${app.recovery.provider:smtp}' == 'brevo' && ${app.recovery.enabled:false}")
public class BrevoAccountMailSender implements AccountMailSender {
    private final String apiKey, from;
    private final MailBudget budget;
    private final HttpClient http;
    private final URI endpoint;
    @org.springframework.beans.factory.annotation.Autowired
    public BrevoAccountMailSender(@Value("${app.recovery.brevo-api-key:}") String apiKey,
            @Value("${app.recovery.from:}") String from, MailBudget budget) {
        this(apiKey, from, budget, HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5))
                .followRedirects(HttpClient.Redirect.NEVER).build(), URI.create("https://api.brevo.com/v3/smtp/email"));
    }
    public BrevoAccountMailSender(String apiKey, String from, MailBudget budget, HttpClient http, URI endpoint) {
        if (apiKey.isBlank() || !from.matches("[^\\s<>@]+@[^\\s<>@]+\\.[^\\s<>@]+"))
            throw new IllegalStateException("Brevo requires BREVO_API_KEY and a verified MAIL_FROM email address.");
        this.apiKey = apiKey; this.from = from; this.budget = budget; this.http = http; this.endpoint = endpoint;
    }
    @Override public boolean available() { return budget.available(); }
    @Override public void send(String recipient, String subject, String body) {
        String key = UUID.randomUUID().toString();
        String payload = JsonMapper.builder().build().writeValueAsString(Map.of(
            "sender", Map.of("name", "AnkiDSA", "email", from), "to", List.of(Map.of("email", recipient)),
            "subject", subject, "textContent", body, "headers", Map.of("idempotencyKey", key)));
        for (int attempt = 0; attempt < 3; attempt++) {
            if (!budget.reserve()) throw new MailUnavailableException();
            var request = HttpRequest.newBuilder(endpoint).timeout(Duration.ofSeconds(10))
                .header("api-key", apiKey).header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(payload)).build();
            try {
                // Never retain or log provider response bodies: they can include addresses/content.
                var response = http.send(request, HttpResponse.BodyHandlers.discarding());
                int code = response.statusCode();
                if (code >= 200 && code < 300) return;
                if (code == 429) { budget.pause(Duration.ofHours(24)); throw new MailUnavailableException(); }
                if (code < 500 || attempt == 2) throw new MailUnavailableException();
                Thread.sleep(500L * (attempt + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt(); throw new MailUnavailableException();
            } catch (java.io.IOException e) {
                // An ambiguous POST is not blindly replayed; the user can request a fresh link.
                throw new MailUnavailableException();
            }
        }
    }
    public static class MailUnavailableException extends RuntimeException {
        public MailUnavailableException() { super("Email delivery is temporarily unavailable."); }
    }
}
