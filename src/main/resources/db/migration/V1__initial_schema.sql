-- ============================================================
-- AnkiDSA — Database Schema
-- Run this in Supabase SQL Editor
-- ============================================================
--
-- HOW TO USE:
-- 1. Go to your Supabase project dashboard
-- 2. Click "SQL Editor" in the left sidebar
-- 3. Click "New query"
-- 4. Paste this ENTIRE file
-- 5. Click "Run"
-- 6. Go to "Table Editor" → you should see 3 tables
--
-- IMPORTANT: Run this BEFORE starting the Spring Boot app.
-- The app uses ddl-auto: validate, which means it checks
-- "do these tables exist and match my entities?" on startup.
-- If the tables don't exist → app crashes with a clear error.
-- ============================================================


-- ============================================================
-- TABLE 1: USERS
-- ============================================================
-- Stores everyone in the system. You first, everyone later.
-- Google OAuth fields (email, name, avatar_url) are populated
-- when someone signs in with "Continue with Google."

CREATE TABLE users (
    id              BIGSERIAL PRIMARY KEY,
    -- BIGSERIAL = auto-incrementing 64-bit integer (1, 2, 3, ...)
    -- Maps to Java's Long type and @GeneratedValue(IDENTITY)

    email           VARCHAR(255) NOT NULL UNIQUE,
    -- UNIQUE = no two users can have the same email
    -- This is your first line of defense against duplicate accounts

    name            VARCHAR(255),
    avatar_url      TEXT,
    fcm_token       TEXT,
    -- Firebase push token. NULL until the user installs the mobile app
    -- and grants push notification permission.

    created_at      TIMESTAMP DEFAULT NOW(),
    updated_at      TIMESTAMP DEFAULT NOW()
);


-- ============================================================
-- TABLE 2: PROBLEMS
-- ============================================================
-- Every DSA problem a user logs + its CURRENT SM-2 state.
-- This is the table the daily queue queries against.

CREATE TABLE problems (
    id                  BIGSERIAL PRIMARY KEY,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    -- REFERENCES users(id) = FOREIGN KEY constraint
    -- ON DELETE CASCADE = if a user is deleted, all their problems
    -- are automatically deleted too. No orphan rows.

    -- Problem metadata (from extension or manual entry)
    leetcode_url        TEXT NOT NULL,
    title               VARCHAR(500) NOT NULL,
    difficulty          VARCHAR(10) NOT NULL CHECK (difficulty IN ('EASY', 'MEDIUM', 'HARD')),
    -- CHECK constraint = database-level validation
    -- Even if someone bypasses Java validation and sends raw SQL,
    -- PostgreSQL itself rejects invalid values. Defense in depth.

    tags                TEXT,
    -- Stored as comma-separated: "sliding-window,two-pointers,hash-map"
    -- Split/joined in the DTO layer. Simple and JPA-compatible.

    notes               TEXT,
    -- User's own pattern notes: "sort first, then two pointers from edges"

    -- SM-2 live state (updated after every review)
    easiness_factor     DOUBLE PRECISION DEFAULT 2.5,
    interval            INTEGER DEFAULT 0,
    repetitions         INTEGER DEFAULT 0,
    next_review_date    DATE DEFAULT CURRENT_DATE,

    -- Problem lifecycle status
    status              VARCHAR(15) DEFAULT 'NEW'
                        CHECK (status IN ('NEW', 'LEARNING', 'REVIEW', 'MASTERED')),

    created_at          TIMESTAMP DEFAULT NOW(),
    updated_at          TIMESTAMP DEFAULT NOW(),

    -- Prevent duplicate URLs per user
    UNIQUE(user_id, leetcode_url)
    -- User A can log "two-sum", User B can also log "two-sum"
    -- But User A can NOT log "two-sum" twice.
);


-- ============================================================
-- TABLE 3: REVIEW_LOGS
-- ============================================================
-- Append-only history. Every review creates a row here.
-- Never updated, never deleted. Powers stats and debugging.

CREATE TABLE review_logs (
    id                  BIGSERIAL PRIMARY KEY,
    problem_id          BIGINT NOT NULL REFERENCES problems(id) ON DELETE CASCADE,
    user_id             BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,

    -- What the user reported (0-5 SM-2 scale)
    quality_rating      INTEGER NOT NULL CHECK (quality_rating BETWEEN 0 AND 5),

    -- Snapshot of SM-2 state AFTER this review
    easiness_factor     DOUBLE PRECISION NOT NULL,
    interval            INTEGER NOT NULL,
    repetitions         INTEGER NOT NULL,

    reviewed_at         TIMESTAMP DEFAULT NOW()
);


-- ============================================================
-- INDEXES
-- ============================================================
-- WHY INDEXES?
-- Without an index, PostgreSQL scans EVERY row to answer a query.
-- 10 problems? Fine. 10,000? Slow. 1 million? Unusable.
-- An index is like a book's table of contents — jump directly
-- to the right page instead of reading every page.
--
-- WHEN TO CREATE AN INDEX:
-- On columns you frequently filter/sort by (WHERE, ORDER BY).
-- Don't index everything — each index slows down INSERT/UPDATE
-- because PostgreSQL must update the index too.

-- The daily queue: "problems due today for this user"
-- This is the MOST CALLED query in the entire app.
CREATE INDEX idx_problems_user_next_review
    ON problems(user_id, next_review_date);

-- Stats: "how many reviews this week/month"
CREATE INDEX idx_review_logs_user_date
    ON review_logs(user_id, reviewed_at);

-- Extension duplicate check: "has this URL been logged already?"
CREATE INDEX idx_problems_user_url
    ON problems(user_id, leetcode_url);
