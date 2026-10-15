package com.ankidsa.config;

import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.util.Arrays;

/** Fail closed for publicly hosted builds, without echoing configuration values. */
@Component
public class HostedConfiguration implements SmartInitializingSingleton {
    private final Environment env;
    public HostedConfiguration(Environment env) { this.env = env; }
    @Override public void afterSingletonsInstantiated() {
        boolean hostedProfile = Arrays.stream(env.getActiveProfiles()).anyMatch(p -> p.equals("production") || p.equals("staging"));
        if (!hostedProfile && !env.getProperty("app.hosted", Boolean.class, false)) return;
        if (Arrays.asList(env.getActiveProfiles()).contains("demo")) fail("demo profile is forbidden");
        String frontendUrl = env.getProperty("app.public-url", "");
        boolean recoveryEnabled = env.getProperty("app.recovery.enabled", Boolean.class, false);
        // An API may be deployed before its frontend; reset emails still require a real frontend URL.
        if (recoveryEnabled || !frontendUrl.isBlank()) httpsOrigin(frontendUrl);
        httpsOrigin(env.getProperty("app.api-public-url", ""));
        String db = env.getProperty("spring.datasource.url", "");
        if (!db.startsWith("jdbc:postgresql:") || !db.matches(".*[?&]sslmode=(require|verify-ca|verify-full)(&.*)?"))
            fail("PostgreSQL with TLS is required");
        String secret = env.getProperty("jwt.secret", "");
        if (secret.length() < 43 || secret.contains("replace") || secret.contains("test-only"))
            fail("a randomly generated JWT secret is required");
        for (String origin : env.getProperty("app.cors-origins", "").split(",")) {
            if (!origin.trim().matches("chrome-extension://[a-p]{32}")) httpsOrigin(origin.trim());
        }
        if (env.getProperty("app.notifications-enabled", Boolean.class, false)
                && env.getProperty("app.reminders.trigger-secret", "").length() < 43)
            fail("a separate random reminder trigger secret is required");
        if (recoveryEnabled
                && !"brevo".equals(env.getProperty("app.recovery.provider")))
            fail("hosted recovery requires the HTTPS mail provider");
    }
    private static void httpsOrigin(String value) {
        try {
            URI uri = URI.create(value);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getHost().equals("localhost")
                    || uri.getHost().contains("*") || uri.getUserInfo() != null || uri.getRawQuery() != null
                    || uri.getFragment() != null || !(uri.getPath().isEmpty() || uri.getPath().equals("/")))
                fail("explicit HTTPS application origins are required");
        } catch (IllegalArgumentException e) { fail("explicit HTTPS application origins are required"); }
    }
    private static void fail(String reason) { throw new IllegalStateException("Hosted configuration: " + reason); }
}
