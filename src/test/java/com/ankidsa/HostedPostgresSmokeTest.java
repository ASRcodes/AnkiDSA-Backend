package com.ankidsa;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Explicit opt-in, read-only, TLS localhost smoke check after disposable migrations. */
@EnabledIfEnvironmentVariable(named="TEST_DB_TLS_URL", matches="jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/ankidsa_test[a-z_]*\\?.*")
@SpringBootTest(properties={
    "spring.datasource.url=${TEST_DB_TLS_URL}", "spring.datasource.username=${TEST_DB_USER}",
    "spring.datasource.password=", "spring.datasource.hikari.read-only=true", "spring.flyway.enabled=false",
    "app.public-url=https://web.validation.invalid", "app.api-public-url=https://api.validation.invalid",
    "app.cors-origins=https://web.validation.invalid",
    "jwt.secret=fixture-key-for-local-hosted-tests-at-least-forty-three-characters",
    "app.notifications-enabled=false", "app.recovery.enabled=false"
})
@ActiveProfiles("production")
@ContextConfiguration(initializers=TestDatabaseGuard.class)
@AutoConfigureMockMvc
class HostedPostgresSmokeTest {
    @Autowired MockMvc mvc;
    @Test void productionProfileStartsWithTlsReadOnlyDatabaseAndExplicitCors() throws Exception {
        mvc.perform(get("/ready")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ready"));
        mvc.perform(get("/health")).andExpect(header().string("Strict-Transport-Security","max-age=31536000"));
        mvc.perform(options("/api/problems").header("Origin","https://web.validation.invalid")
            .header("Access-Control-Request-Method","GET")).andExpect(status().isOk());
        mvc.perform(options("/api/problems").header("Origin","https://untrusted.invalid")
            .header("Access-Control-Request-Method","GET")).andExpect(status().isForbidden());
        mvc.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
    }
}
