package com.ankidsa.service;

import com.google.firebase.messaging.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.concurrent.*;
import lombok.extern.slf4j.Slf4j;

@Service @Slf4j
public class FcmNotificationService implements NotificationService {
    private final ObjectProvider<FirebaseMessaging> messaging;
    public FcmNotificationService(ObjectProvider<FirebaseMessaging> messaging) { this.messaging = messaging; }
    @Override public Delivery sendReminderNotification(String token, String deliveryId) { return sendToToken(token, deliveryId); }
    public Delivery sendToToken(String token, String deliveryId) {
        FirebaseMessaging sender = messaging.getIfAvailable();
        if (sender == null) {
            log.warn("FCM reminder result=FAILED reason=MISSING_SENDER");
            return Delivery.FAILED;
        }
        var message = Message.builder().setToken(token)
            .setNotification(Notification.builder().setTitle("Your questions for today are ready")
                .setBody("Tap to see which questions to revisit and review your approach.").build())
            .putData("action", "OPEN_QUEUE").putData("deliveryId", deliveryId)
            .setAndroidConfig(AndroidConfig.builder().setTtl(3600000).setCollapseKey("daily-review")
                .setNotification(AndroidNotification.builder().setTag("daily-review")
                    .setChannelId("ankidsa_reminders").setIcon("ic_notification").build()).build())
            .build();
        var future = sender.sendAsync(message);
        try { future.get(15, TimeUnit.SECONDS); return Delivery.SENT; }
        catch (InterruptedException e) {
            Thread.currentThread().interrupt(); future.cancel(true);
            log.info("FCM reminder result=RETRY reason=INTERRUPTED");
            return Delivery.RETRY;
        }
        catch (TimeoutException e) {
            future.cancel(true);
            log.warn("FCM reminder result=RETRY reason=TIMEOUT");
            return Delivery.RETRY;
        }
        catch (ExecutionException e) {
            if (e.getCause() instanceof FirebaseMessagingException fcm) {
                var code = fcm.getMessagingErrorCode();
                var result = classify(code);
                // Log only the SDK enum: provider exceptions can contain tokens and payloads.
                String reason = code == null ? "UNKNOWN" : code.name();
                var providerCode = fcm.getErrorCode();
                String providerReason = providerCode == null ? "UNKNOWN" : providerCode.name();
                if (result == Delivery.UNREGISTERED) log.info("FCM reminder result={} reason={} providerCode={}", result, reason, providerReason);
                else log.warn("FCM reminder result={} reason={} providerCode={}", result, reason, providerReason);
                return result;
            }
            log.warn("FCM reminder result=RETRY reason=SDK_FAILURE");
            return Delivery.RETRY;
        }
    }
    public static Delivery classify(MessagingErrorCode code) {
        if (code == null) return Delivery.RETRY;
        return switch (code) {
            case UNREGISTERED -> Delivery.UNREGISTERED;
            case UNAVAILABLE, INTERNAL, QUOTA_EXCEEDED -> Delivery.RETRY;
            default -> Delivery.FAILED;
        };
    }
}
