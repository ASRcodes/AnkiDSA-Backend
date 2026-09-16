package com.ankidsa.repository;

import com.ankidsa.entity.PasswordResetToken;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;

public interface PasswordResetTokenRepository extends JpaRepository<PasswordResetToken, String> {
    @Modifying
    @Query("delete from PasswordResetToken t where t.user.id = :id")
    void deleteForUser(@Param("id") Long id);

    @Modifying
    @Query("delete from PasswordResetToken t where t.user.id = :id and t.expiresAt <= :now")
    void deleteExpiredForUser(@Param("id") Long id, @Param("now") Instant now);
}
