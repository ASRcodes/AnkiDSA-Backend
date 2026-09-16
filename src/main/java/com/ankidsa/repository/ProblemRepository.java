package com.ankidsa.repository;
import com.ankidsa.entity.*;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;
import java.time.LocalDate;
import java.util.*;
public interface ProblemRepository extends JpaRepository<Problem,Long> {
    Optional<Problem> findByIdAndUserId(Long id,Long userId);
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Problem p where p.id = :id and p.user.id = :userId")
    Optional<Problem> lockOwned(@Param("id") Long id,@Param("userId") Long userId);
    List<Problem> findByUserIdAndNextReviewDateLessThanEqualOrderByNextReviewDateAscIdAsc(Long userId,LocalDate date);
    List<Problem> findByUserIdOrderByNextReviewDateAsc(Long userId);
    boolean existsByUserIdAndLeetcodeUrl(Long userId,String url);
    long countByUserId(Long userId);
    long countByUserIdAndDifficulty(Long userId,Difficulty difficulty);
    long countByUserIdAndStatus(Long userId,ProblemStatus status);
    long countByUserIdAndNextReviewDateLessThanEqual(Long userId,LocalDate date);
    long countByUserIdAndNextReviewDateBefore(Long userId,LocalDate date);
}
