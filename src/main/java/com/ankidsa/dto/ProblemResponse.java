package com.ankidsa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * WHAT THE CLIENT SEES when they request a problem.
 *
 * Compare this to the Problem entity:
 * - Entity has: User object (with email, fcmToken, etc.)
 * - Response has: just userId (a number)
 *
 * - Entity has: tags as a single comma-separated String
 * - Response has: tags as a List<String> (cleaner for the client)
 *
 * - Entity has: internal Hibernate proxies, lazy-loading traps
 * - Response has: plain data, safe to serialize to JSON
 *
 * This is the "TRANSLATION LAYER" between your database model
 * and your API contract. The client never sees your entities.
 *
 * WHY IS THIS IMPORTANT FOR INTERVIEWS?
 * "Why don't you return entities directly?" is asked in almost
 * every Spring Boot interview. The answer:
 * 1. Security (don't leak internal data)
 * 2. Stability (entity changes don't break the API)
 * 3. Lazy-loading (returning an entity with LAZY relations
 *    outside a transaction causes LazyInitializationException)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProblemResponse {

    private Long id;
    private Long version;
    private String leetcodeUrl;
    private String title;
    private String difficulty;
    private List<String> tags;
    private String notes;

    // SM-2 state — the client needs this to show
    // "Next review in 3 days" or "EF: 2.5"
    private Double easinessFactor;
    private Integer interval;
    private Integer repetitions;
    private LocalDate nextReviewDate;

    private String status;
    private LocalDateTime createdAt;
}
