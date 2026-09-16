package com.ankidsa.service;
import com.google.firebase.messaging.*;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service @Slf4j
public class NotificationService {
    public enum Delivery { SENT, UNREGISTERED, RETRY }

    public Delivery sendPushNotification(String token) {
        try {
            FirebaseMessaging.getInstance().send(Message.builder().setToken(token)
                .setNotification(Notification.builder().setTitle("A little practice, lasting recall")
                    .setBody("Take a moment to revisit your review queue.").build())
                .putData("action", "OPEN_QUEUE")
                .setAndroidConfig(AndroidConfig.builder().setTtl(3600000)
                    .setNotification(AndroidNotification.builder().setTag("daily-review").build()).build())
                .setApnsConfig(ApnsConfig.builder().putHeader("apns-collapse-id", "daily-review")
                    .putHeader("apns-expiration", Long.toString(java.time.Instant.now().plusSeconds(3600).getEpochSecond())).build())
                .build());
            return Delivery.SENT;
        } catch (FirebaseMessagingException e) {
            if (e.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) return Delivery.UNREGISTERED;
            log.warn("Notification delivery failed: {}", e.getMessagingErrorCode());
            return Delivery.RETRY;
        } catch (Exception e) {
            log.warn("Notification delivery failed: {}", e.getClass().getSimpleName());
            return Delivery.RETRY;
        }
    }
}
