package com.ankidsa.service;
import com.ankidsa.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import java.time.*;
import java.util.*;

@Service @RequiredArgsConstructor @Slf4j
@ConditionalOnProperty(name = "app.notifications-enabled", havingValue = "true")
public class SchedulerService {
    private final ReminderDeviceRepository devices;
    private final ProblemRepository problems;
    private final NotificationService notifications;
    private final Clock clock;
    private final PlatformTransactionManager transactionManager;

    @Scheduled(cron = "0 30 6 * * *", zone = "${app.timezone}")
    public void sendDailyReviewNotifications() {
        Instant cutoff = clock.instant().minus(Duration.ofDays(30));
        new TransactionTemplate(transactionManager).executeWithoutResult(status -> devices.removeStale(cutoff));
        Map<Long, Boolean> due = new HashMap<>();
        int sent = 0;
        for (var device : devices.activeSince(cutoff)) {
            long userId = device.getUser().getId();
            if (!due.computeIfAbsent(userId, id ->
                    problems.countByUserIdAndNextReviewDateLessThanEqual(id, LocalDate.now(clock)) > 0)) continue;
            var result = notifications.sendPushNotification(device.getToken());
            if (result == NotificationService.Delivery.SENT) sent++;
            if (result == NotificationService.Delivery.UNREGISTERED)
                new TransactionTemplate(transactionManager).executeWithoutResult(status ->
                    devices.removeOwned(userId, device.getTokenHash()));
        }
        log.info("Delivered daily reminders to {} devices", sent);
    }
}
