package com.ankidsa.entity;

/**
 * Tracks where a problem is in the learning lifecycle.
 * This powers the dashboard stats — "12 Mastered, 45 Learning, 8 New"
 *
 * The transitions work like this:
 *
 *   NEW ──→ LEARNING ──→ REVIEW ──→ MASTERED
 *    ↑          ↑            ↑          │
 *    └──────────┴────────────┴──────────┘
 *                (if you forget it, it drops back)
 *
 * NEW      → just logged, never reviewed yet
 * LEARNING → reviewed, but interval is still short (< 7 days)
 * REVIEW   → in the regular review cycle (interval 7-21 days)
 * MASTERED → interval > 21 days AND easiness factor > 2.5
 *            This means you've recalled it correctly multiple times
 *            with increasing gaps — it's in long-term memory.
 *
 * If you rate a MASTERED problem as "Hard" (quality < 3),
 * it drops back to LEARNING. This is the whole point of SRS —
 * nothing stays "mastered" if you start forgetting it.
 */
public enum ProblemStatus {
    NEW,
    LEARNING,
    REVIEW,
    MASTERED
}
