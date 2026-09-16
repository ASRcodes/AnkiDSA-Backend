ALTER TABLE users ADD COLUMN token_version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE users ADD COLUMN last_reset_request_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE password_reset_tokens (
    token_hash VARCHAR(64) PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    expires_at TIMESTAMP WITH TIME ZONE NOT NULL
);
CREATE INDEX idx_password_reset_user ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_expiry ON password_reset_tokens(expires_at);
