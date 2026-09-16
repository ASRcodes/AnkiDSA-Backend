package com.ankidsa.dto;

import com.ankidsa.entity.Problem;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * CONVERTS BETWEEN ENTITY AND DTO.
 *
 * WHY A SEPARATE MAPPER CLASS?
 * You could put this logic in the service or controller.
 * But conversion is its own responsibility (Single Responsibility Principle).
 * And you'll reuse it — ProblemController, ReviewController, StatsController
 * all need to convert Problems to ProblemResponses.
 *
 * WHY NOT USE MAPSTRUCT?
 * MapStruct is a library that auto-generates mappers at compile time.
 * It's great for large projects with 50+ entities.
 * For AnkiDSA (3 entities), a manual mapper is simpler and
 * more educational — you see exactly what's happening.
 *
 * INTERVIEW NOTE:
 * "How do you convert entities to DTOs?" is a common question.
 * Answers: manual mapper (what we're doing), MapStruct, ModelMapper.
 * Each has tradeoffs. Manual = full control, no magic, more code.
 * MapStruct = fast (compile-time), less code, harder to debug.
 * ModelMapper = runtime reflection, slowest, most error-prone.
 */
public class ProblemMapper {

    // Private constructor — this is a utility class with only static methods.
    // You never create an instance: ProblemMapper.toResponse(problem)
    private ProblemMapper() {}

    /**
     * Entity → Response DTO
     *
     * Called when returning problem data to the client.
     * Converts the comma-separated tags string to a List
     * and strips out internal fields the client shouldn't see.
     */
    public static ProblemResponse toResponse(Problem problem) {
        return ProblemResponse.builder()
                .id(problem.getId())
                .version(problem.getVersion())
                .leetcodeUrl(problem.getLeetcodeUrl())
                .title(problem.getTitle())
                .difficulty(problem.getDifficulty().name())
                // .name() converts enum EASY → String "EASY"
                .tags(parseTags(problem.getTags()))
                .notes(problem.getNotes())
                .easinessFactor(problem.getEasinessFactor())
                .interval(problem.getInterval())
                .repetitions(problem.getRepetitions())
                .nextReviewDate(problem.getNextReviewDate())
                .status(problem.getStatus().name())
                .createdAt(problem.getCreatedAt())
                .build();
    }

    /**
     * Splits comma-separated tags string into a List.
     * "sliding-window,two-pointers" → ["sliding-window", "two-pointers"]
     *
     * Handles null and empty strings safely.
     */
    private static List<String> parseTags(String tags) {
        if (tags == null || tags.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(tags.split(",")).map(String::trim).filter(t -> !t.isBlank()).distinct().toList();
    }
}
