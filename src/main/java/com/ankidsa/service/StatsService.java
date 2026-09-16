package com.ankidsa.service;
import com.ankidsa.dto.StatsResponse;
import com.ankidsa.entity.*;
import com.ankidsa.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.*;
import java.util.*;
@Service @RequiredArgsConstructor
public class StatsService {
    private final ProblemRepository problems;
    private final ReviewLogRepository reviews;
    private final Clock clock;
    @Transactional(readOnly=true)
    public StatsResponse getStats(Long userId) {
        LocalDate today=LocalDate.now(clock);
        long total=reviews.countByUserId(userId);
        Map<String,Long> activity=new TreeMap<>();
        reviews.findByUserIdAndReviewedAtBetween(userId,today.minusDays(83).atStartOfDay(),today.plusDays(1).atStartOfDay())
            .forEach(r -> activity.merge(r.getReviewedAt().toLocalDate().toString(),1L,Long::sum));
        int streak=0;
        Set<LocalDate> activeDates=new HashSet<>(reviews.reviewDates(userId));
        LocalDate cursor=activeDates.contains(today) ? today : today.minusDays(1);
        while(activeDates.contains(cursor)) { streak++; cursor=cursor.minusDays(1); }
        return StatsResponse.builder()
            .totalProblems(problems.countByUserId(userId))
            .easyCount(problems.countByUserIdAndDifficulty(userId,Difficulty.EASY))
            .mediumCount(problems.countByUserIdAndDifficulty(userId,Difficulty.MEDIUM))
            .hardCount(problems.countByUserIdAndDifficulty(userId,Difficulty.HARD))
            .dueToday(problems.countByUserIdAndNextReviewDateLessThanEqual(userId,today))
            .overdueCount(problems.countByUserIdAndNextReviewDateBefore(userId,today))
            .masteredCount(problems.countByUserIdAndStatus(userId,ProblemStatus.MASTERED))
            .learningCount(problems.countByUserIdAndStatus(userId,ProblemStatus.LEARNING))
            .totalReviews(total)
            .reviewsToday(reviews.countByUserIdAndReviewedAtGreaterThanEqualAndReviewedAtLessThan(userId,today.atStartOfDay(),today.plusDays(1).atStartOfDay()))
            .averageQuality(reviews.averageQualityRating(userId))
            .retentionRate(total==0 ? null : 100.0*reviews.countByUserIdAndQualityRatingGreaterThanEqual(userId,3)/total)
            .activity(activity).streak(streak).today(today.toString()).build();
    }
}
