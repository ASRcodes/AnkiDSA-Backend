package com.ankidsa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * Returned after a review is submitted.
 * Shows the user what the SM-2 algorithm decided.
 *
 * Example response:
 * {
 *   "problemId": 42,
 *   "title": "Two Sum",
 *   "easinessFactor": 2.36,
 *   "interval": 6,
 *   "repetitions": 2,
 *   "nextReviewDate": "2026-08-16",
 *   "status": "LEARNING",
 *   "message": "See you in 6 days!"
 * }
 *
 * The "message" field is a human-readable summary.
 * The Flutter app can show this as a toast/snackbar
 * after the user taps "Good" or "Hard."
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReviewResponse {

    private Long problemId;
    private String title;
    private Double easinessFactor;
    private Integer interval;
    private Integer repetitions;
    private LocalDate nextReviewDate;
    private String status;
    private String message;
}
