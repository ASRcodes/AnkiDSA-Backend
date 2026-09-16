package com.ankidsa.repository;
import com.ankidsa.entity.ReviewLog;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.LocalDateTime;
import java.util.*;
public interface ReviewLogRepository extends JpaRepository<ReviewLog,Long> {
    @Query(value="select distinct cast(reviewed_at as date) from review_logs where user_id = :id",nativeQuery=true)
    List<java.time.LocalDate> reviewDates(@Param("id") Long id);
    Optional<ReviewLog> findByUserIdAndRequestId(Long userId,String requestId);
    List<ReviewLog> findByProblemIdOrderByReviewedAtDesc(Long problemId);
    List<ReviewLog> findByUserIdAndReviewedAtBetween(Long userId,LocalDateTime start,LocalDateTime end);
    long countByUserId(Long userId);
    long countByUserIdAndQualityRatingGreaterThanEqual(Long userId,int quality);
    long countByUserIdAndReviewedAtGreaterThanEqualAndReviewedAtLessThan(Long userId,LocalDateTime start,LocalDateTime end);
    @Query("select avg(r.qualityRating) from ReviewLog r where r.user.id = :id")
    Double averageQualityRating(@Param("id") Long id);
    @Modifying @Query("delete from ReviewLog r where r.problem.id = :id")
    void deleteForProblem(@Param("id") Long id);
}
