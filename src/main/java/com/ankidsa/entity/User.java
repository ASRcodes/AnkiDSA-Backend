package com.ankidsa.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import java.time.Instant;
import java.time.LocalDateTime;

@Entity
@Table(name = "users")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class User {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    @Column(nullable = false, unique = true)
    private String email;
    @Column(name = "password_hash")
    private String passwordHash;
    private String name;
    @Column(name = "avatar_url", columnDefinition = "TEXT")
    private String avatarUrl;
    @Column(name = "fcm_token", columnDefinition = "TEXT")
    private String fcmToken;
    @Builder.Default
    @Column(name = "token_version", nullable = false)
    private long tokenVersion = 0;
    @Column(name = "last_reset_request_at")
    private Instant lastResetRequestAt;
    @CreationTimestamp
    @Column(name = "created_at", updatable = false)
    private LocalDateTime createdAt;
    @UpdateTimestamp
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public String displayName() {
        return name == null || name.isBlank() ? email.split("@")[0] : name;
    }
}
