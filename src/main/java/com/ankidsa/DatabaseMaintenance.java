package com.ankidsa;

import org.flywaydb.core.Flyway;
import java.sql.DriverManager;
import java.util.Arrays;

/** Explicit, offline database maintenance. Never invoked by normal API startup. */
public final class DatabaseMaintenance {
    private static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalArgumentException("Missing " + key);
        return value;
    }
    public static void run(boolean migrate) throws Exception {
        String url = required("DB_URL"), username = System.getenv("DB_USERNAME"), password = required("DB_PASSWORD");
        if (username == null) username = required("DB_USER");
        com.ankidsa.config.DatabaseConnectionConfiguration.validate(url, username);
        if (!url.startsWith("jdbc:postgresql:")) throw new IllegalArgumentException("PostgreSQL required");
        try (var connection = DriverManager.getConnection(url, username, password);
             var statement = connection.createStatement()) {
            connection.setReadOnly(true);
            statement.setQueryTimeout(15);
            try (var result = statement.executeQuery("select current_database(), current_schema(), current_user")) {
                result.next();
                if (!result.getString(1).equals(required("EXPECTED_DATABASE")) || !result.getString(3).equals(required("EXPECTED_DATABASE_ROLE")))
                    throw new IllegalStateException("Database identity mismatch");
                if (!"public".equals(result.getString(2))) throw new IllegalStateException("Expected public schema");
            }
        }
        var flyway = Flyway.configure().dataSource(url, username, password)
            .locations("classpath:db/migration").cleanDisabled(true).baselineOnMigrate(false).load();
        var pending = flyway.info().pending();
        System.out.println("Database identity matched; pending migration versions: " +
            Arrays.stream(pending).map(m -> m.getVersion().toString()).toList());
        if (!migrate) return;
        if (!"true".equals(System.getenv("BACKUP_VERIFIED")) || !"true".equals(System.getenv("MIGRATION_APPROVED")))
            throw new IllegalStateException("Verify external backup and review migrations before approval");
        if (Arrays.stream(pending).anyMatch(m -> m.getVersion().getVersion().equals("4")) &&
                !"true".equals(System.getenv("LEGACY_V4_REVIEWED")))
            throw new IllegalStateException("V4 clears legacy FCM tokens. Review separately; account/study data must be retained");
        flyway.migrate();
        System.out.println("Migration completed. Record the application revision and verify row counts externally.");
    }
}
