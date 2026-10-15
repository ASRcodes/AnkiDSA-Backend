package com.ankidsa.exception;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import java.util.*;
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {
    @ExceptionHandler(org.springframework.web.servlet.resource.NoResourceFoundException.class)
    ResponseEntity<?> missingRoute(Exception e) { return error(404, "This endpoint does not exist."); }
    @ExceptionHandler(ResourceNotFoundException.class)
    ResponseEntity<?> notFound(ResourceNotFoundException e) { return error(404, e.getMessage()); }
    @ExceptionHandler(DuplicateProblemException.class)
    ResponseEntity<?> duplicate(DuplicateProblemException e) { return error(409, "This problem is already in your collection."); }
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<?> status(ResponseStatusException e) { return error(e.getStatusCode().value(), e.getReason() == null ? "Request could not be completed." : e.getReason()); }
    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<?> validation(MethodArgumentNotValidException e) {
        Map<String,String> fields = new LinkedHashMap<>();
        e.getBindingResult().getFieldErrors().forEach(f -> fields.put(f.getField(), f.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("message", fields.values().stream().findFirst().orElse("Check your input."), "fieldErrors", fields));
    }
    @ExceptionHandler({HttpMessageNotReadableException.class, IllegalArgumentException.class})
    ResponseEntity<?> malformed(Exception e) { return error(400, "Check the request fields and try again."); }
    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<?> conflict(Exception e) { return error(409, "That entry already exists or was changed. Refresh and try again."); }
    @ExceptionHandler(Exception.class)
    ResponseEntity<?> unexpected(Exception e) {
        // Exception messages/SQL can contain credentials, reset links or user data.
        log.error("Request failed type={}", e.getClass().getSimpleName());
        return error(500, "Something went wrong. Please try again.");
    }
    @ExceptionHandler(org.springframework.dao.DataAccessResourceFailureException.class)
    ResponseEntity<?> unavailable(Exception e) { return error(503, "AnkiDSA is temporarily unavailable. Please retry shortly."); }
    @ExceptionHandler(org.springframework.web.HttpRequestMethodNotSupportedException.class)
    ResponseEntity<?> method(Exception e) { return error(405, "This method is not supported."); }
    private ResponseEntity<?> error(int status, String message) { return ResponseEntity.status(status).body(Map.of("message", message)); }
}
