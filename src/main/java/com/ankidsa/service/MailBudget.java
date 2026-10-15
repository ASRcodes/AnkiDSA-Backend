package com.ankidsa.service;

import org.springframework.stereotype.Service;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.*;
import java.sql.Timestamp;
import java.util.UUID;

/** A rolling 24-hour budget shared by all instances, conservative even after crashes. */
@Service
public class MailBudget {
    private final JdbcTemplate db;
    private final TransactionTemplate transactions;
    private final Clock clock;
    private final int limit;
    public MailBudget(JdbcTemplate db, PlatformTransactionManager manager, Clock clock,
            @Value("${app.recovery.daily-limit:250}") int limit) {
        this.db = db; this.transactions = new TransactionTemplate(manager); this.clock = clock;
        if (limit < 1 || limit > 250) throw new IllegalArgumentException("MAIL_DAILY_LIMIT must be 1..250 for this free beta.");
        this.limit = limit;
    }
    public boolean available() {
        return db.queryForObject("select blocked_until from mail_budget where id=1", Timestamp.class).toInstant().isBefore(clock.instant())
            && db.queryForObject("select count(*) from mail_reservations where reserved_at>?", Integer.class,
                Timestamp.from(clock.instant().minus(Duration.ofDays(1)))) < limit;
    }
    public boolean reserve() {
        return Boolean.TRUE.equals(transactions.execute(status -> {
            Timestamp blocked = db.queryForObject("select blocked_until from mail_budget where id=1 for update", Timestamp.class);
            if (blocked.toInstant().isAfter(clock.instant())) return false;
            db.update("delete from mail_reservations where reserved_at<=?", Timestamp.from(clock.instant().minus(Duration.ofDays(1))));
            if (db.queryForObject("select count(*) from mail_reservations", Integer.class) >= limit) return false;
            db.update("insert into mail_reservations(id,reserved_at) values (?,?)", UUID.randomUUID().toString(), Timestamp.from(clock.instant()));
            return true;
        }));
    }
    public void pause(Duration delay) {
        db.update("update mail_budget set blocked_until=? where id=1", Timestamp.from(clock.instant().plus(delay)));
    }
}
