package com.ankidsa.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.extern.slf4j.Slf4j;
import java.time.*;
import java.sql.Timestamp;
import java.util.*;

/** External requests drive this service; there is deliberately no in-process cron. */
@Service @Slf4j
public class SchedulerService {
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final NotificationService notifications;
    private final Clock clock;
    private final boolean enabled;
    private final LocalTime start, end;
    private final int batchSize, maxAttempts, leaseSeconds;
    public SchedulerService(JdbcTemplate db, PlatformTransactionManager manager, NotificationService notifications,
            Clock clock, @Value("${app.notifications-enabled:false}") boolean enabled,
            @Value("${app.reminders.start:06:30}") String start, @Value("${app.reminders.end:12:00}") String end,
            @Value("${app.reminders.batch-size:10}") int batchSize, @Value("${app.reminders.max-attempts:5}") int maxAttempts,
            @Value("${app.reminders.lease-seconds:180}") int leaseSeconds) {
        this.db = db; this.transactions = new TransactionTemplate(manager); this.notifications = notifications;
        this.clock = clock; this.enabled = enabled; this.start = LocalTime.parse(start); this.end = LocalTime.parse(end);
        this.batchSize = Math.clamp(batchSize, 1, 20); this.maxAttempts = Math.clamp(maxAttempts, 1, 10);
        this.leaseSeconds = Math.max(60, leaseSeconds);
    }
    public record Result(String status, int sent, int retryable, int failed, int remaining) {}
    private record Device(long userId, String hash, long version) {}
    private record Pending(String id, long userId, String hash, long version, int attempts) {}
    private Timestamp now() { return Timestamp.from(clock.instant()); }
    private Timestamp lease() { return Timestamp.from(clock.instant().plusSeconds(leaseSeconds)); }
    public Result runBatch() {
        if (!enabled) return new Result("disabled", 0, 0, 0, 0);
        LocalTime time = LocalTime.now(clock);
        if (time.isBefore(start) || !time.isBefore(end)) return new Result("outside_window", 0, 0, 0, 0);
        String owner = UUID.randomUUID().toString();
        if (db.update("update job_leases set owner=?, lease_until=? where name='reminders' and lease_until<=?",
                owner, lease(), now()) != 1) return new Result("busy", 0, 0, 0, 0);
        int sent = 0, retryable = 0, failed = 0;
        LocalDate day = LocalDate.now(clock);
        try {
            stage(day);
            var pending = db.query("""
                select id,user_id,token_hash,session_version,attempts from reminder_deliveries
                where study_date=? and attempts<? and
                ((status in ('PENDING','RETRYABLE') and next_attempt_at<=?) or (status='PROCESSING' and lease_until<=?))
                order by next_attempt_at,id limit ?
                """, (rs, row) -> new Pending(rs.getString(1), rs.getLong(2), rs.getString(3), rs.getLong(4), rs.getInt(5)),
                day, maxAttempts, now(), now(), batchSize);
            // Monotonic time bounds the HTTP request even if a test/application clock is adjusted.
            long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(40);
            for (Pending item : pending) {
                if (System.nanoTime() > deadline || !LocalTime.now(clock).isBefore(end)) break;
                if (db.update("update job_leases set lease_until=? where name='reminders' and owner=? and lease_until>?",
                        lease(), owner, now()) != 1) break;
                String claim = UUID.randomUUID().toString();
                int claimed = db.update("""
                    update reminder_deliveries set status='PROCESSING',attempts=attempts+1,claim_id=?,lease_until=?
                    where id=? and ((status in ('PENDING','RETRYABLE') and next_attempt_at<=?)
                    or (status='PROCESSING' and lease_until<=?))
                    """, claim, lease(), item.id(), now(), now());
                if (claimed != 1) continue;
                var tokens = db.query("""
                    select d.token from reminder_devices d join users u on u.id=d.user_id
                    where d.token_hash=? and d.user_id=? and d.session_version=? and u.token_version=d.session_version
                    and d.updated_at>? and exists(select 1 from problems p where p.user_id=u.id and p.next_review_date<=?)
                    """, (rs, row) -> rs.getString(1), item.hash(), item.userId(), item.version(),
                    Timestamp.from(clock.instant().minus(Duration.ofDays(30))), day);
                if (tokens.isEmpty()) { finish(item, claim, "FAILED", "INELIGIBLE", null); failed++; continue; }
                NotificationService.Delivery result;
                try { result = notifications.sendReminderNotification(tokens.getFirst(), item.id()); }
                catch (RuntimeException e) { result = NotificationService.Delivery.RETRY; }
                switch (result) {
                    case SENT -> { finish(item, claim, "SENT", null, now()); sent++; }
                    case UNREGISTERED -> {
                        transactions.executeWithoutResult(s -> {
                            db.update("delete from reminder_devices where token_hash=? and user_id=? and session_version=?",
                                item.hash(), item.userId(), item.version());
                            finish(item, claim, "FAILED", "UNREGISTERED", null);
                        }); failed++;
                    }
                    case FAILED -> { finish(item, claim, "FAILED", "PROVIDER_REJECTED", null); failed++; }
                    case RETRY -> {
                        boolean exhausted = item.attempts() + 1 >= maxAttempts;
                        finish(item, claim, exhausted ? "FAILED" : "RETRYABLE", "TRANSIENT", null);
                        if (exhausted) failed++; else retryable++;
                    }
                }
            }
            int remaining = db.queryForObject("select count(*) from reminder_deliveries where study_date=? and status in ('PENDING','PROCESSING','RETRYABLE')",
                    Integer.class, day);
            // Include devices not staged yet so the external runner continues through large batches.
            remaining += unstaged(day);
            log.info("Reminder batch sent={} retryable={} failed={} remaining={}", sent, retryable, failed, remaining);
            return new Result("complete", sent, retryable, failed, remaining);
        } finally {
            db.update("update job_leases set lease_until=?,owner=null where name='reminders' and owner=?", now(), owner);
        }
    }
    private void finish(Pending item, String claim, String status, String code, Timestamp sentAt) {
        long delay = Math.min(1800L, 60L << Math.min(item.attempts(), 5));
        db.update("""
            update reminder_deliveries set status=?,failure_code=?,sent_at=?,lease_until=null,next_attempt_at=?
            where id=? and claim_id=? and status='PROCESSING'
            """, status, code, sentAt, Timestamp.from(clock.instant().plusSeconds(delay)), item.id(), claim);
    }
    private static final String ELIGIBLE = """
        from reminder_devices d join users u on u.id=d.user_id
        where d.session_version=u.token_version
        and exists(select 1 from problems p where p.user_id=d.user_id and p.next_review_date<=?)
        and not exists(select 1 from reminder_deliveries r where r.study_date=? and r.user_id=d.user_id
            and r.token_hash=d.token_hash and r.session_version=d.session_version)
        """;
    private int unstaged(LocalDate day) { return db.queryForObject("select count(*) " + ELIGIBLE, Integer.class, day, day); }
    private void stage(LocalDate day) {
        db.update("update reminder_deliveries set status='FAILED',failure_code='EXPIRED' where study_date<? and status in ('PENDING','PROCESSING','RETRYABLE')", day);
        db.update("update reminder_deliveries set status='FAILED',failure_code='ATTEMPTS_EXHAUSTED' where attempts>=? and status='PROCESSING' and lease_until<=?", maxAttempts, now());
        db.update("delete from reminder_deliveries where study_date<?", day.minusDays(30));
        db.update("delete from revoked_sessions where expires_at<=?", now());
        db.update("delete from password_reset_tokens where expires_at<=?", now());
        db.update("delete from reminder_devices where updated_at<=?", Timestamp.from(clock.instant().minus(Duration.ofDays(30))));
        var devices = db.query("select d.user_id,d.token_hash,d.session_version " + ELIGIBLE + " order by d.token_hash limit 200",
            (rs, row) -> new Device(rs.getLong(1), rs.getString(2), rs.getLong(3)), day, day);
        for (Device d : devices) {
            String id = PasswordRecoveryService.fingerprint(day + ":" + d.userId() + ":" + d.hash() + ":" + d.version());
            try { db.update("""
                insert into reminder_deliveries(id,user_id,token_hash,session_version,study_date,status,next_attempt_at)
                values (?,?,?,?,?,'PENDING',?)
                """, id, d.userId(), d.hash(), d.version(), day, now()); }
            catch (org.springframework.dao.DuplicateKeyException ignored) { /* Unique constraint is the final guard. */ }
        }
    }
}
