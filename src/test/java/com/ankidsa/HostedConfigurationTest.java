package com.ankidsa;
import com.ankidsa.config.HostedConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import static org.junit.jupiter.api.Assertions.*;
class HostedConfigurationTest {
    private MockEnvironment valid() {
        return new MockEnvironment().withProperty("app.hosted", "true")
            .withProperty("app.public-url", "https://web.validation.invalid")
            .withProperty("app.api-public-url", "https://api.validation.invalid")
            .withProperty("spring.datasource.url", "jdbc:postgresql://localhost/ankidsa_test?sslmode=require")
            .withProperty("jwt.secret", "fixture-configuration-key-with-more-than-forty-three-characters")
            .withProperty("app.cors-origins", "https://web.validation.invalid,chrome-extension://bafnmcdkpidnpollodebmifggmmpkbje");
    }
    @Test void validHostedOriginsAndDatabaseTlsAreAccepted() {
        assertDoesNotThrow(() -> new HostedConfiguration(valid()).afterSingletonsInstantiated());
    }
    @Test void frontendMayBeDeployedLaterOnlyWhenPasswordRecoveryIsDisabled() {
        var env = valid().withProperty("app.public-url", "")
            .withProperty("app.cors-origins", "chrome-extension://bafnmcdkpidnpollodebmifggmmpkbje");
        assertDoesNotThrow(() -> new HostedConfiguration(env).afterSingletonsInstantiated());
        env.withProperty("app.recovery.enabled", "true").withProperty("app.recovery.provider", "brevo");
        assertThrows(IllegalStateException.class, () -> new HostedConfiguration(env).afterSingletonsInstantiated());
    }
    @Test void hostedCannotFallBackToHttpWildcardCorsOrPlainDatabase() {
        for (var pair : new String[][]{{"app.public-url", "http://localhost:3000"}, {"app.cors-origins", "*"}, {"spring.datasource.url", "jdbc:postgresql://localhost/ankidsa_test"}, {"jwt.secret", "short"}})
            assertThrows(IllegalStateException.class, () -> new HostedConfiguration(valid().withProperty(pair[0],pair[1])).afterSingletonsInstantiated());
    }
    @Test void demoAndUnprotectedNotificationsCannotBeHosted() {
        var env=valid(); env.setActiveProfiles("production", "demo");
        assertThrows(IllegalStateException.class, () -> new HostedConfiguration(env).afterSingletonsInstantiated());
        assertThrows(IllegalStateException.class, () -> new HostedConfiguration(valid().withProperty("app.notifications-enabled","true")).afterSingletonsInstantiated());
        assertThrows(IllegalStateException.class, () -> new HostedConfiguration(valid().withProperty("app.recovery.enabled","true").withProperty("app.recovery.provider","smtp")).afterSingletonsInstantiated());
        var bypass = valid().withProperty("app.hosted", "false").withProperty("app.public-url", "http://localhost");
        bypass.setActiveProfiles("production");
        assertThrows(IllegalStateException.class, () -> new HostedConfiguration(bypass).afterSingletonsInstantiated());
    }
}
