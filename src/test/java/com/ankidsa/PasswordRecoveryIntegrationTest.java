package com.ankidsa;

import com.ankidsa.entity.User;
import com.ankidsa.repository.*;
import com.ankidsa.service.*;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.mockito.ArgumentCaptor;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(properties = {
    "spring.profiles.active=test",
    "spring.datasource.url=${TEST_DB_URL:jdbc:h2:mem:recovery;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1;NON_KEYWORDS=INTERVAL}",
    "spring.datasource.username=${TEST_DB_USER:sa}",
    "spring.datasource.password=",
    "jwt.secret=test-only-secret-for-integration-tests-at-least-32-bytes",
    "app.notifications-enabled=false",
    "app.recovery.enabled=true",
    "app.public-url=http://localhost:3000"
})
@AutoConfigureMockMvc
@org.springframework.test.context.ContextConfiguration(initializers=TestDatabaseGuard.class)
class PasswordRecoveryIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PasswordResetTokenRepository tokens;
    @Autowired ReminderDeviceRepository devices;
    @Autowired ReminderDeviceService registrations;
    @MockitoBean AccountMailSender mail;
    @org.junit.jupiter.api.BeforeEach void mailIsAvailable() { when(mail.available()).thenReturn(true); }

    record Account(String email, String token) {}

    Account account() throws Exception {
        String email = UUID.randomUUID() + "@example.com";
        String json = mvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"OriginalPassword123\",\"name\":\"Recovery test\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return new Account(email, JsonPath.read(json, "$.token"));
    }

    String request(String email) throws Exception {
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\"}")).andExpect(status().isOk());
        var body = ArgumentCaptor.forClass(String.class);
        verify(mail, timeout(5000)).send(eq(email), eq("Reset your AnkiDSA password"), body.capture());
        var matcher = Pattern.compile("http://localhost:3000/#/reset-password\\?token=([A-Za-z0-9_-]{43})").matcher(body.getValue());
        assertTrue(matcher.find(), "The email must use the configured app origin and a URL-fragment token.");
        return matcher.group(1);
    }

    String resetBody(String token) {
        return "{\"token\":\"" + token + "\",\"password\":\"ReplacementPassword123\"}";
    }

    @Test void resetIsSingleUseAndRevokesEveryOldSession() throws Exception {
        var account = account();
        String phone = "reset-phone-" + UUID.randomUUID();
        mvc.perform(put("/api/users/me/device").header("Authorization", "Bearer " + account.token())
            .contentType(MediaType.APPLICATION_JSON).content("{\"token\":\"" + phone + "\"}"))
            .andExpect(status().isNoContent());
        String raw = request(account.email());
        String hash = PasswordRecoveryService.fingerprint(raw);
        assertTrue(tokens.existsById(hash));
        assertFalse(tokens.existsById(raw), "A raw recovery token must not be stored.");

        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetBody(raw)))
            .andExpect(status().isNoContent());
        assertFalse(devices.existsById(PasswordRecoveryService.fingerprint(phone)));
        long userId = users.findByEmail(account.email()).orElseThrow().getId();
        assertThrows(org.springframework.web.server.ResponseStatusException.class,
            () -> registrations.register(userId, 0, phone));
        mvc.perform(get("/api/problems").header("Authorization", "Bearer " + account.token()))
            .andExpect(status().isUnauthorized());
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetBody(raw)))
            .andExpect(status().isBadRequest());
        mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + account.email() + "\",\"password\":\"OriginalPassword123\"}"))
            .andExpect(status().isUnauthorized());
        String session = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + account.email() + "\",\"password\":\"ReplacementPassword123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(session, "$.token");
        mvc.perform(get("/api/auth/verify").header("Authorization", "Bearer " + token)).andExpect(status().isOk());
        verify(mail, timeout(5000)).send(eq(account.email()), eq("Your AnkiDSA password has changed"), anyString());
    }

    @Test void expiredLinksLeaveThePasswordAndSessionIntact() throws Exception {
        var account = account();
        String raw = request(account.email());
        var reset = tokens.findById(PasswordRecoveryService.fingerprint(raw)).orElseThrow();
        reset.setExpiresAt(Instant.now().minusSeconds(1));
        tokens.save(reset);
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetBody(raw)))
            .andExpect(status().isBadRequest());
        mvc.perform(get("/api/auth/verify").header("Authorization", "Bearer " + account.token()))
            .andExpect(status().isOk());
    }

    @Test void concurrentUsesOfOneLinkHaveOnlyOneWinner() throws Exception {
        var account = account();
        String raw = request(account.email());
        try (var pool = Executors.newFixedThreadPool(2)) {
            Callable<Integer> submit = () -> mvc.perform(post("/api/auth/reset-password")
                .contentType(MediaType.APPLICATION_JSON).content(resetBody(raw))).andReturn().getResponse().getStatus();
            var results = pool.invokeAll(List.of(submit, submit));
            var codes = new ArrayList<Integer>();
            for (var result : results) codes.add(result.get());
            Collections.sort(codes);
            assertEquals(List.of(204, 400), codes);
        }
        verify(mail, timeout(5000)).send(eq(account.email()), eq("Your AnkiDSA password has changed"), anyString());
        assertEquals(1, users.findByEmail(account.email()).orElseThrow().getTokenVersion());
    }

    @Test void requestsDoNotDiscloseAccountsAndHaveAPerAccountCooldown() throws Exception {
        var account = account();
        String first = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + account.email() + "\"}")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        String unknown = mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"nobody-" + UUID.randomUUID() + "@example.com\"}")).andExpect(status().isOk())
            .andReturn().getResponse().getContentAsString();
        assertEquals(first, unknown);
        verify(mail, timeout(5000)).send(eq(account.email()), eq("Reset your AnkiDSA password"), anyString());
        mvc.perform(post("/api/auth/forgot-password").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + account.email() + "\"}")).andExpect(status().isOk());
        // A queue marker waits for earlier asynchronous requests to finish without timing assumptions.
        var marker = account();
        request(marker.email());
        verify(mail, times(1)).send(eq(account.email()), eq("Reset your AnkiDSA password"), anyString());
    }

    @Test void emailOwnershipCanRecoverALegacyAccountWithoutANameOrPassword() throws Exception {
        String email = "legacy-" + UUID.randomUUID() + "@example.com";
        users.save(User.builder().email(email).build());
        String raw = request(email);
        mvc.perform(post("/api/auth/reset-password").contentType(MediaType.APPLICATION_JSON).content(resetBody(raw)))
            .andExpect(status().isNoContent());
        String session = mvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
            .content("{\"email\":\"" + email + "\",\"password\":\"ReplacementPassword123\"}"))
            .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        String token = JsonPath.read(session, "$.token");
        mvc.perform(get("/api/auth/verify").header("Authorization", "Bearer " + token))
            .andExpect(status().isOk()).andExpect(jsonPath("$.name").isNotEmpty());
        verify(mail, timeout(5000)).send(eq(email), eq("Your AnkiDSA password has changed"), anyString());
    }
}
