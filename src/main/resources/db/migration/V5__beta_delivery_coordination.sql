-- Additive only. Rehearse on a copy and verify a backup before applying remotely.
CREATE TABLE revoked_sessions (
    session_id VARCHAR(36) PRIMARY KEY,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_revoked_session_expiry ON revoked_sessions(expires_at);

CREATE TABLE job_leases (
    name VARCHAR(40) PRIMARY KEY,
    owner VARCHAR(36),
    lease_until TIMESTAMP WITH TIME ZONE NOT NULL
);
INSERT INTO job_leases(name, lease_until) VALUES ('reminders', TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00');

CREATE TABLE reminder_deliveries (
    id VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash VARCHAR(64) NOT NULL,
    session_version BIGINT NOT NULL,
    study_date DATE NOT NULL,
    status VARCHAR(16) NOT NULL CHECK (status IN ('PENDING','PROCESSING','SENT','FAILED','RETRYABLE')),
    attempts INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP WITH TIME ZONE NOT NULL,
    lease_until TIMESTAMP WITH TIME ZONE,
    claim_id VARCHAR(36),
    sent_at TIMESTAMP WITH TIME ZONE,
    failure_code VARCHAR(40),
    UNIQUE(study_date, user_id, token_hash, session_version)
);
CREATE INDEX idx_reminder_pending ON reminder_deliveries(study_date, status, next_attempt_at);
CREATE INDEX idx_reminder_delivery_user ON reminder_deliveries(user_id);

CREATE TABLE mail_budget (
    id INTEGER PRIMARY KEY CHECK (id = 1),
    window_start TIMESTAMP WITH TIME ZONE NOT NULL,
    used INTEGER NOT NULL DEFAULT 0,
    blocked_until TIMESTAMP WITH TIME ZONE NOT NULL
);
INSERT INTO mail_budget(id, window_start, used, blocked_until)
    VALUES (1, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00', 0, TIMESTAMP WITH TIME ZONE '1970-01-01 00:00:00+00');
CREATE TABLE mail_reservations (
    id VARCHAR(36) PRIMARY KEY,
    reserved_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_mail_reservation_time ON mail_reservations(reserved_at);
