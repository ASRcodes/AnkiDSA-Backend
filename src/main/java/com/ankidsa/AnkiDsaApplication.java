package com.ankidsa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * THE ENTRY POINT — where everything begins.
 *
 * When you click "Run" in IntelliJ, Java calls main() here.
 * SpringApplication.run() then:
 *
 * 1. COMPONENT SCANNING — walks through every class in com.ankidsa.*
 *    looking for annotations:
 *    - @Entity          → registers with Hibernate (database mapping)
 *    - @Repository      → creates a database-access proxy
 *    - @Service         → registers as a business logic bean
 *    - @RestController  → registers HTTP endpoints
 *    - @Configuration   → applies config (security, swagger, etc.)
 *
 * 2. AUTO-CONFIGURATION — reads application.yml, sets up:
 *    - Database connection pool (HikariCP)
 *    - Hibernate / JPA
 *    - Embedded Tomcat server on port 8080
 *    - Security filter chain
 *    - Swagger documentation
 *
 * 3. STARTS TOMCAT — your app is now listening for HTTP requests.
 *
 * WHAT IS @SpringBootApplication?
 * It's actually 3 annotations in one:
 * - @Configuration     → "this class has bean definitions"
 * - @EnableAutoConfiguration → "auto-configure based on dependencies"
 * - @ComponentScan     → "scan com.ankidsa and sub-packages"
 *
 * WHAT IS @EnableScheduling?
 * Tells Spring to look for @Scheduled methods and run them
 * on a timer (cron jobs). We'll use this in Week 2 for the
 * daily queue builder that runs at 6:30 AM.
 */
@SpringBootApplication
@EnableScheduling
public class AnkiDsaApplication {

    public static void main(String[] args) throws Exception {
        if (java.util.Arrays.asList(args).contains("--verify-database") || java.util.Arrays.asList(args).contains("--migrate-database")) {
            try { DatabaseMaintenance.run(java.util.Arrays.asList(args).contains("--migrate-database")); }
            catch (com.ankidsa.config.DatabaseConnectionConfiguration.InvalidDatabaseConfigurationException e) {
                System.err.println(e.getMessage());
                System.exit(1);
            }
            catch (Exception e) {
                // Drivers can include credentials/addresses in exception text.
                System.err.println("Database maintenance did not complete. Check identity, backup approval, migration state, and connection settings privately. Error type: " + e.getClass().getSimpleName());
                System.exit(1);
            }
            return;
        }
        SpringApplication.run(AnkiDsaApplication.class, args);
    }
}
