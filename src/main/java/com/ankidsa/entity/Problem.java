package com.ankidsa.entity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.*;
@Entity @Table(name="problems", uniqueConstraints=@UniqueConstraint(columnNames={"user_id","leetcode_url"}))
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class Problem {
    @Id @GeneratedValue(strategy=GenerationType.IDENTITY) private Long id;
    @ManyToOne(fetch=FetchType.LAZY) @JoinColumn(name="user_id", nullable=false) private User user;
    @Column(name="leetcode_url", nullable=false, columnDefinition="TEXT") private String leetcodeUrl;
    @Column(nullable=false, length=500) private String title;
    @Enumerated(EnumType.STRING) @Column(nullable=false,length=10) private Difficulty difficulty;
    @Column(columnDefinition="TEXT") private String tags;
    @Column(columnDefinition="TEXT") private String notes;
    @Builder.Default @Column(name="easiness_factor") private Double easinessFactor=2.5;
    @Builder.Default private Integer interval=0;
    @Builder.Default private Integer repetitions=0;
    @Builder.Default @Column(name="next_review_date") private LocalDate nextReviewDate=LocalDate.now();
    @Builder.Default @Enumerated(EnumType.STRING) @Column(length=15) private ProblemStatus status=ProblemStatus.NEW;
    @Version @Builder.Default private Long version=0L;
    @CreationTimestamp @Column(name="created_at",updatable=false) private LocalDateTime createdAt;
    @UpdateTimestamp @Column(name="updated_at") private LocalDateTime updatedAt;
}
