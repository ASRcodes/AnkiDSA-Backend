package com.ankidsa;

import com.ankidsa.config.DatabaseConnectionConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.core.env.SystemEnvironmentPropertySource;

import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class DatabaseConnectionConfigurationTest {
    private static final String POOLER =
            "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require";
    private static final String USER = "postgres.testprojectreference";

    @Test void sessionPoolerAcceptsProjectQualifiedUsers() {
        for (String user : new String[]{USER, "app_user.testprojectreference"})
            assertDoesNotThrow(() -> DatabaseConnectionConfiguration.validate(POOLER, user));
    }

    @Test void missingTenantReportsTheExactRenderSetting() {
        for (String user : new String[]{"postgres", "", "postgres.", "postgres. ref"}) {
            var error = assertThrows(IllegalStateException.class,
                    () -> DatabaseConnectionConfiguration.validate(POOLER, user));
            assertTrue(error.getMessage().contains("ENOIDENTIFIER"));
            assertTrue(error.getMessage().contains("DB_USERNAME"));
            assertTrue(error.getMessage().contains("DB_USER"));
        }
    }

    @Test void urlUserOverridesTheSeparateUsernameJustLikePgJdbc() {
        assertThrows(IllegalStateException.class,
                () -> DatabaseConnectionConfiguration.validate(POOLER + "&user=postgres", USER));
        assertDoesNotThrow(() -> DatabaseConnectionConfiguration.validate(
                POOLER + "&user=postgres%2Etestprojectreference", "postgres"));
    }

    @Test void transactionPoolerAndMissingTlsHaveActionableErrors() {
        var portError = assertThrows(IllegalStateException.class,
                () -> DatabaseConnectionConfiguration.validate(POOLER.replace(":5432", ":6543"), USER));
        assertTrue(portError.getMessage().contains("Session pooler on port 5432"));
        for (String url : new String[]{POOLER.replace("?sslmode=require", ""), POOLER + "&sslmode=disable"}) {
            var tlsError = assertThrows(IllegalStateException.class,
                    () -> DatabaseConnectionConfiguration.validate(url, USER));
            assertTrue(tlsError.getMessage().contains("sslmode=require"));
        }
    }

    @Test void directPostgresAndLocalDemoDoNotNeedTenantQualifiedUsers() {
        assertDoesNotThrow(() -> DatabaseConnectionConfiguration.validate(
                "jdbc:postgresql://db.testprojectreference.supabase.co:5432/postgres?sslmode=require", "postgres"));
        assertDoesNotThrow(() -> DatabaseConnectionConfiguration.validate(
                "jdbc:postgresql://localhost:5432/ankidsa_test", "local_user"));
        assertDoesNotThrow(() -> DatabaseConnectionConfiguration.validate("jdbc:h2:mem:test", "sa"));
    }

    @Test void invalidAndCredentialBearingUrlsNeverLeakTheirValues() {
        String secret = "do-not-print-this-password";
        for (String url : new String[]{"", "postgresql://postgres:" + secret + "@host/db",
                "jdbc:postgresql://postgres:" + secret + "@host/db",
                POOLER + "&password=" + secret + "%invalid"}) {
            var error = assertThrows(IllegalStateException.class,
                    () -> DatabaseConnectionConfiguration.validate(url, "postgres"));
            assertFalse(error.getMessage().contains(secret));
            assertNull(error.getCause());
        }
    }

    @Test void startupRejectsMissingTenantBeforeAnyConnectionBeanCanBeCreated() {
        var connectionCreated = new AtomicBoolean(false);
        new ApplicationContextRunner()
                .withUserConfiguration(AnkiDsaApplication.class)
                .withPropertyValues("spring.datasource.url=" + POOLER, "spring.datasource.username=postgres")
                .withBean("connectionProbe", Object.class, () -> {
                    connectionCreated.set(true);
                    throw new AssertionError("Startup reached bean creation before connection validation");
                })
                .run(context -> {
                    assertNotNull(context.getStartupFailure());
                    Throwable root = context.getStartupFailure();
                    while (root.getCause() != null) root = root.getCause();
                    assertInstanceOf(DatabaseConnectionConfiguration.InvalidDatabaseConfigurationException.class, root);
                    assertTrue(root.getMessage().contains("ENOIDENTIFIER"));
                    assertFalse(connectionCreated.get());
                });
    }

    @Test void separateFlywayConnectionIsValidatedBeforeMigration() {
        new ApplicationContextRunner()
                .withUserConfiguration(DatabaseConnectionConfiguration.class)
                .withPropertyValues("spring.datasource.url=" + POOLER, "spring.datasource.username=" + USER,
                        "spring.flyway.url=" + POOLER, "spring.flyway.user=postgres")
                .run(context -> assertNotNull(context.getStartupFailure()));
        new ApplicationContextRunner()
                .withUserConfiguration(DatabaseConnectionConfiguration.class)
                .withPropertyValues("spring.datasource.url=" + POOLER, "spring.datasource.username=" + USER,
                        "spring.flyway.url=" + POOLER, "spring.flyway.user=postgres", "spring.flyway.enabled=false")
                .run(context -> assertNull(context.getStartupFailure()));
    }

    @Test void environmentAcceptsBothNamesAndHonorsSpringOverride() {
        for (var variables : java.util.List.of(
                Map.of("DB_USER", USER), Map.of("DB_USERNAME", USER),
                Map.of("DB_USER", "postgres", "DB_USERNAME", USER),
                Map.of("DB_USER", "postgres", "SPRING_DATASOURCE_USERNAME", USER))) {
            var env = new MockEnvironment();
            env.getPropertySources().addFirst(new SystemEnvironmentPropertySource("render", new java.util.HashMap<>(variables)));
            env.setProperty("spring.datasource.username", "${DB_USERNAME:${DB_USER:}}");
            assertEquals(USER, env.getProperty("spring.datasource.username"));
        }
    }
}
