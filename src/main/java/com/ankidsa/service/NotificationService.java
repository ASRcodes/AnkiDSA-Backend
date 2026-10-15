package com.ankidsa.service;

/** Provider-independent results; callers persist retries instead of sleeping in requests. */
public interface NotificationService {
    enum Delivery { SENT, UNREGISTERED, RETRY, FAILED }
    Delivery sendReminderNotification(String token, String deliveryId);
}
