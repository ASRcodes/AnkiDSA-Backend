package com.ankidsa.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * UPDATED SWAGGER CONFIG — adds JWT authentication support.
 *
 * This adds an "Authorize" button to Swagger UI.
 * After getting a token from /api/auth/login, you click Authorize,
 * paste the token, and all subsequent "Try it out" requests
 * automatically include the Authorization: Bearer header.
 *
 * Without this, you'd have to manually type the header
 * for every test request — painful.
 */
@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI ankiDsaOpenAPI() {
        return new OpenAPI()
                .info(new Info()
                        .title("AnkiDSA API")
                        .description("Spaced Repetition Engine for DSA Interview Prep. " +
                                     "Log problems, review on schedule, never forget a pattern.\n\n" +
                                     "**Authentication**: Call POST /api/auth/register or /api/auth/login " +
                                     "to get a token, then click Authorize above and paste it.")
                        .version("v1.0.0")
                        .contact(new Contact()
                                .name("AnkiDSA")
                                .url("https://ankidsa.in")))
                .components(new Components()
                        .addSecuritySchemes("bearerAuth",
                                new SecurityScheme()
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Paste your JWT token here (without 'Bearer ' prefix)")
                        ));
    }
}
