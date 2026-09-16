package com.ankidsa.entity;
import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;

@Entity @Table(name = "reminder_devices")
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
public class ReminderDevice {
    @Id @Column(name = "token_hash", length = 64)
    private String tokenHash;
    @ManyToOne(fetch = FetchType.LAZY, optional = false) @JoinColumn(name = "user_id")
    private User user;
    @Column(nullable = false, columnDefinition = "TEXT")
    private String token;
    @Column(name = "session_version", nullable = false)
    private long sessionVersion;
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
