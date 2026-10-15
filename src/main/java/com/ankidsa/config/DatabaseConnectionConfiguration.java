package com.ankidsa.config;

import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Check connection routing before Flyway, Hibernate, or Hikari opens a connection. */
@Configuration(proxyBeanMethods = false)
public class DatabaseConnectionConfiguration {
    @Bean
    static BeanFactoryPostProcessor validateDatabaseConnections(Environment env) {
        return beanFactory -> {
            String url = env.getProperty("spring.datasource.url", "");
            String user = env.getProperty("spring.datasource.username", "");
            validate(url, user);
            if (env.getProperty("spring.flyway.enabled", Boolean.class, true)) {
                validate(env.getProperty("spring.flyway.url", url),
                        env.getProperty("spring.flyway.user", user));
            }
        };
    }

    public static void validate(String url, String username) {
        if (url == null || url.isBlank())
            fail("Set DB_URL to the PostgreSQL JDBC connection URL.");
        if (url.startsWith("jdbc:h2:")) return; // Local demo/tests; hosted guard rejects H2.
        if (!url.startsWith("jdbc:postgresql://"))
            fail("DB_URL must start with jdbc:postgresql://, not postgres:// or postgresql://. "
                    + "Put the database username and password in DB_USERNAME and DB_PASSWORD.");

        URI uri;
        Map<String, String> query = new HashMap<>();
        try {
            uri = URI.create(url.substring("jdbc:".length()));
            if (uri.getRawQuery() != null) {
                for (String part : uri.getRawQuery().split("&")) {
                    String[] pair = part.split("=", 2);
                    query.put(decode(pair[0]), pair.length == 2 ? decode(pair[1]) : "");
                }
            }
        } catch (IllegalArgumentException e) {
            // Never attach parser exceptions: they can contain passwords from a URL.
            fail("DB_URL is not a valid PostgreSQL JDBC URL. Copy the host and port from Supabase Connect; "
                    + "keep credentials in DB_USERNAME and DB_PASSWORD.");
            return;
        }
        if (uri.getUserInfo() != null)
            fail("Do not embed username:password@ in DB_URL. Use DB_USERNAME and DB_PASSWORD separately.");
        if (uri.getHost() == null || uri.getFragment() != null)
            fail("DB_URL must contain a valid database hostname and no URL fragment.");

        String host = uri.getHost().toLowerCase(Locale.ROOT);
        if (!host.endsWith(".pooler.supabase.com")) return;

        // pgJDBC gives URL parameters precedence over the separate credentials.
        String effectiveUser = query.getOrDefault("user", username);
        if (effectiveUser == null || !effectiveUser.matches("[^\\s]+\\.[A-Za-z0-9_-]+"))
            fail("Supabase pooler connections require a tenant identifier. Set DB_USERNAME "
                    + "(legacy DB_USER) to the full Session pooler username, usually postgres.<project-ref>, "
                    + "from Supabase > Connect > Session pooler. A plain postgres username causes ENOIDENTIFIER. "
                    + "Also check SPRING_DATASOURCE_USERNAME, SPRING_FLYWAY_USER and any user= parameter in DB_URL.");
        if (uri.getPort() != -1 && uri.getPort() != 5432)
            fail("Use the Supabase Session pooler on port 5432 for this backend and Flyway. "
                    + "The Transaction pooler on port 6543 is not compatible with this configuration.");
        if (!java.util.Set.of("require", "verify-ca", "verify-full").contains(query.getOrDefault("sslmode", "")))
            fail("Add sslmode=require (or verify-ca/verify-full with the appropriate certificate) to DB_URL "
                    + "for an encrypted Supabase connection.");
    }

    private static String decode(String value) {
        return URLDecoder.decode(value, StandardCharsets.UTF_8);
    }

    private static void fail(String message) {
        throw new InvalidDatabaseConfigurationException(message);
    }

    /** Messages contain remediation only, never supplied connection values or credentials. */
    public static final class InvalidDatabaseConfigurationException extends IllegalStateException {
        private InvalidDatabaseConfigurationException(String message) {
            super("Database configuration: " + message);
        }
    }
}
