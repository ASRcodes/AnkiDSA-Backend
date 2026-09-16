package com.ankidsa.repository;
import com.ankidsa.entity.ReminderDevice;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;
import java.time.Instant;
import java.util.*;

public interface ReminderDeviceRepository extends JpaRepository<ReminderDevice, String> {
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select d from ReminderDevice d where d.tokenHash = :hash")
    Optional<ReminderDevice> lockByHash(@Param("hash") String hash);

    @Modifying
    @Query("delete from ReminderDevice d where d.user.id = :userId and d.tokenHash = :hash")
    int removeOwned(@Param("userId") Long userId, @Param("hash") String hash);

    @Modifying
    @Query("delete from ReminderDevice d where d.user.id = :userId")
    void removeForUser(@Param("userId") Long userId);

    @Query("select d from ReminderDevice d join fetch d.user u where d.updatedAt > :cutoff and d.sessionVersion = u.tokenVersion")
    List<ReminderDevice> activeSince(@Param("cutoff") Instant cutoff);

    @Modifying
    @Query("delete from ReminderDevice d where d.updatedAt <= :cutoff")
    void removeStale(@Param("cutoff") Instant cutoff);
}
