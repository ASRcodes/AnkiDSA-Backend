package com.ankidsa.service;

import com.google.firebase.messaging.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import java.util.concurrent.*;

@Service
public class FcmNotificationService implements NotificationService {
    private final ObjectProvider<FirebaseMessaging> messaging;
    public FcmNotificationService(ObjectProvider<FirebaseMessaging> messaging) { this.messaging = messaging; }
    @Override public Delivery sendReminderNotification(String token, String deliveryId) { return sendToToken(token, deliveryId); }
    public Delivery sendToToken(String token, String deliveryId) {
        FirebaseMessaging sender = messaging.getIfAvailable();
        if (sender == null) return Delivery.FAILED;
        var message = Message.builder().setToken(token)
            .setNotification(Notification.builder().setTitle("A little practice, lasting recall")
                .setBody("Take a moment to revisit your review queue.").build())
            .putData("action", "OPEN_QUEUE").putData("deliveryId", deliveryId)
            .setAndroidConfig(AndroidConfig.builder().setTtl(3600000).setCollapseKey("daily-review")
                .setNotification(AndroidNotification.builder().setTag("daily-review")
                    .setChannelId("ankidsa_reminders").setIcon("ic_notification").build()).build())
            .build();
        var future = sender.sendAsync(message);
        try { future.get(15, TimeUnit.SECONDS); return Delivery.SENT; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); future.cancel(true); return Delivery.RETRY; }
        catch (TimeoutException e) { future.cancel(true); return Delivery.RETRY; }
        catch (ExecutionException e) {
            if (e.getCause() instanceof FirebaseMessagingException fcm) return classify(fcm.getMessagingErrorCode());
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
