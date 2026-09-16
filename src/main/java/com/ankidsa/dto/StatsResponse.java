package com.ankidsa.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Dashboard statistics — everything the user sees at a glance.
 *
 * Example response:
 * {
 *   "totalProblems": 127,
 *   "easyCount": 42,
 *   "mediumCount": 63,
 *   "hardCount": 22,
 *   "dueToday": 8,
 *   "overdueCount": 3,
 *   "masteredCount": 34,
 *   "learningCount": 51,
 *   "totalReviews": 892,
 *   "reviewsToday": 5,
 *   "averageQuality": 3.7,
 *   "retentionRate": 74.0
 * }
 *
 * retentionRate is calculated as:
 * (reviews with quality >= 3) / (total reviews) * 100
 * This tells you: "of all the times I reviewed something,
 * how often did I actually recall it correctly?"
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsResponse {

    private long totalProblems;

    // By difficulty
    private long easyCount;
    private long mediumCount;
    private long hardCount;

    // By status
    private long dueToday;
    private long overdueCount;
    private long masteredCount;
    private long learningCount;

    // Review stats
    private long totalReviews;
    private long reviewsToday;
    private Double averageQuality;
    private Double retentionRate;
    private java.util.Map<String, Long> activity;
    private int streak;
    private String today;
}
