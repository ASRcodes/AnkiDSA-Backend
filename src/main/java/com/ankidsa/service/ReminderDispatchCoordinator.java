package com.ankidsa.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.ContextClosedEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Drains eligible reminders while the service is awake, including after a cold start. */
@Component
@ConditionalOnProperty(prefix = "app", name = "notifications-enabled", havingValue = "true")
@Slf4j
public class ReminderDispatchCoordinator {
    private final SchedulerService scheduler;
    private volatile boolean ready;

    public ReminderDispatchCoordinator(SchedulerService scheduler) {
        this.scheduler = scheduler;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        // Delivery must never delay the startup event or readiness transition.
        ready = true;
        log.info("Automatic reminder dispatcher ready");
    }

    @EventListener(ContextClosedEvent.class)
    public void onContextClosed() {
        ready = false;
    }

    @Scheduled(fixedDelay = 60_000, initialDelay = 1_000)
    public void dispatch() {
        if (!ready) return;
        try {
            // The shared service owns delivery windows, leases, retries and deduplication.
            scheduler.runBatch();
        } catch (RuntimeException ignored) {
            // Provider/database exceptions can contain credentials or user data.
            log.warn("Automatic reminder batch failed; retrying on the next scheduled tick");
        }
    }
}
