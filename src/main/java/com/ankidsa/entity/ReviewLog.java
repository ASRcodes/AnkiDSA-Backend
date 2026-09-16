package com.ankidsa.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.*;
@Entity @Table(name="review_logs", uniqueConstraints=@UniqueConstraint(columnNames={"user_id","request_id"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReviewLog {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="problem_id",nullable=false) private Problem problem;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id",nullable=false) private User user;
    @Column(name="quality_rating",nullable=false) private Integer qualityRating;
    @Column(name="easiness_factor",nullable=false) private Double easinessFactor;
    @Column(nullable=false) private Integer interval;
    @Column(nullable=false) private Integer repetitions;
    @Column(name="reviewed_at",updatable=false) private LocalDateTime reviewedAt;
    @Column(name="request_id",length=36) private String requestId;
    @Column(name="next_review_date") private LocalDate nextReviewDate;
    @Column(length=15) private String status;
}
