package com.ankidsa;
import org.springframework.context.ApplicationContextInitializer;
import org.springframework.context.ConfigurableApplicationContext;

/** Tests may delete fixtures, so accept only memory databases or explicitly named loopback test DBs. */
public class TestDatabaseGuard implements ApplicationContextInitializer<ConfigurableApplicationContext> {
    @Override public void initialize(ConfigurableApplicationContext context) {
        String url = context.getEnvironment().getProperty("spring.datasource.url", "");
        if (!url.startsWith("jdbc:h2:mem:") && !url.matches("jdbc:postgresql://(127\\.0\\.0\\.1|localhost)(:[0-9]+)?/ankidsa_test[a-z_]*(\\?.*)?"))
            throw new IllegalStateException("Tests require a disposable loopback ankidsa_test database.");
    }
}
