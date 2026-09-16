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
        log.error("Request failed", e);
        return error(500, "Something went wrong. Please try again.");
    }
    private ResponseEntity<?> error(int status, String message) { return ResponseEntity.status(status).body(Map.of("message", message)); }
}
