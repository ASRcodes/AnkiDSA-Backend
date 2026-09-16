CREATE TABLE reminder_devices (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token TEXT NOT NULL,
    session_version BIGINT NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_reminder_device_user ON reminder_devices(user_id);
CREATE INDEX idx_reminder_device_updated ON reminder_devices(updated_at);
-- Old registrations predate consent and account-safe device removal.
UPDATE users SET fcm_token = NULL;
