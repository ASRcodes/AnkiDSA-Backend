package com.ankidsa.exception;

/**
 * Thrown when a user tries to log a problem they've already logged.
 *
 * The Chrome extension sends a URL after you solve a problem.
 * If you solve "Two Sum" twice (maybe you forgot you already logged it),
 * the extension sends the same URL again.
 *
 * Without this: the database throws a constraint violation
 * (UNIQUE(user_id, leetcode_url)) which surfaces as an ugly 500 error
 * with a Hibernate stack trace.
 *
 * With this: the service checks FIRST, throws DuplicateProblemException,
 * the GlobalExceptionHandler catches it, returns a clean 409 Conflict:
 * {
 *   "status": 409,
 *   "error": "Conflict",
 *   "message": "You've already logged this problem: Two Sum"
 * }
 *
 * The extension can then show: "Already tracked!" instead of crashing.
 */
public class DuplicateProblemException extends RuntimeException {

    public DuplicateProblemException(String message) {
        super(message);
    }

    public static DuplicateProblemException forUrl(String url) {
        return new DuplicateProblemException("Problem already logged: " + url);
    }
}
