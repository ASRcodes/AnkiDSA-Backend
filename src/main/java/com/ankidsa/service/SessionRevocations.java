package com.ankidsa.service;

import com.ankidsa.config.JwtService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.dao.DuplicateKeyException;
import java.sql.Timestamp;
import java.time.Clock;

@Service
public class SessionRevocations {
    private final JdbcTemplate db;
    private final Clock clock;
    public SessionRevocations(JdbcTemplate db, Clock clock) { this.db = db; this.clock = clock; }
    public boolean revoked(String id) {
        return db.queryForObject("select count(*) from revoked_sessions where session_id = ?", Integer.class, id) > 0;
    }
    public void revoke(JwtService.Identity identity) {
        db.update("delete from revoked_sessions where expires_at < ?", Timestamp.from(clock.instant()));
        try { db.update("insert into revoked_sessions(session_id, expires_at) values (?, ?)",
                identity.sessionId(), Timestamp.from(identity.expiresAt())); }
        catch (DuplicateKeyException ignored) { /* Repeated logout is harmless. */ }
    }
}
