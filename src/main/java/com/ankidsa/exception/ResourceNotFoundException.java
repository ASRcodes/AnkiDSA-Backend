package com.ankidsa.exception;

/**
 * CUSTOM EXCEPTION — thrown when a requested resource doesn't exist.
 *
 * Examples:
 * - User tries to review problem #999, but it doesn't exist
 * - Extension checks for a user by email, but they haven't signed up
 *
 * WHY NOT JUST USE RuntimeException?
 * You could write: throw new RuntimeException("Problem not found")
 * But then in the GlobalExceptionHandler, you can't distinguish
 * "not found" from "database crashed" from "null pointer" — they're
 * all RuntimeException. Custom exceptions let you handle each case
 * differently:
 * - ResourceNotFoundException → 404 Not Found
 * - DuplicateProblemException → 409 Conflict
 * - RuntimeException → 500 Internal Server Error (something unexpected)
 *
 * WHY EXTEND RuntimeException, NOT Exception?
 * Java has two types of exceptions:
 * - Checked (extends Exception) → you MUST catch it or declare "throws"
 * - Unchecked (extends RuntimeException) → optional to catch
 *
 * Spring's @Transactional only rolls back on unchecked exceptions
 * by default. And forcing "throws" everywhere adds noise.
 * Modern Java convention: use unchecked for business logic exceptions.
 */
public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String message) {
        super(message);
    }

    // Convenience factory methods — cleaner than writing
    // "new ResourceNotFoundException("Problem with ID " + id + " not found")"
    // every time.

    public static ResourceNotFoundException forProblem(Long id) {
        return new ResourceNotFoundException("Problem with ID " + id + " not found");
    }

    public static ResourceNotFoundException forUser(Long id) {
        return new ResourceNotFoundException("User with ID " + id + " not found");
    }

    public static ResourceNotFoundException forUserEmail(String email) {
        return new ResourceNotFoundException("User with email " + email + " not found");
    }
}
