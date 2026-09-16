package com.ankidsa.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * STANDARDIZED ERROR RESPONSE.
 *
 * Without this, Spring Boot returns ugly default errors:
 * {
 *   "timestamp": "2026-08-10T20:41:19.081+00:00",
 *   "status": 500,
 *   "error": "Internal Server Error",
 *   "path": "/api/problems"
 * }
 *
 * That tells the client NOTHING about what went wrong.
 * With ApiErrorResponse, every error looks like:
 * {
 *   "status": 404,
 *   "error": "Not Found",
 *   "message": "Problem with ID 999 not found",
 *   "timestamp": "2026-08-10T20:41:19",
 *   "fieldErrors": null
 * }
 *
 * For validation errors (400):
 * {
 *   "status": 400,
 *   "error": "Validation Failed",
 *   "message": "Request has invalid fields",
 *   "timestamp": "2026-08-10T20:41:19",
 *   "fieldErrors": {
 *     "title": "Problem title is required",
 *     "difficulty": "Difficulty must be EASY, MEDIUM, or HARD"
 *   }
 * }
 *
 * WHAT IS @JsonInclude(NON_NULL)?
 * When serializing to JSON, skip any field that is null.
 * So if there are no fieldErrors, the JSON won't include
 * "fieldErrors": null — it just won't have the key at all.
 * Cleaner output for the client.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ApiErrorResponse {

    private int status;
    private String error;
    private String message;
    private LocalDateTime timestamp;
    private Map<String, String> fieldErrors;
}
