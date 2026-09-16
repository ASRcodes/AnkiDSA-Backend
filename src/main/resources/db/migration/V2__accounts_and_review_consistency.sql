ALTER TABLE users ADD COLUMN password_hash VARCHAR(255);
ALTER TABLE problems ADD COLUMN version BIGINT NOT NULL DEFAULT 0;
ALTER TABLE review_logs ADD COLUMN request_id VARCHAR(36);
ALTER TABLE review_logs ADD COLUMN next_review_date DATE;
ALTER TABLE review_logs ADD COLUMN status VARCHAR(15);
ALTER TABLE review_logs ADD CONSTRAINT unique_review_request UNIQUE(user_id, request_id);
