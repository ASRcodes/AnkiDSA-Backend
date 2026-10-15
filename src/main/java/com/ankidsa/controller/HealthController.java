package com.ankidsa.controller;
import org.springframework.web.bind.annotation.*;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.ResponseEntity;
@RestController
public class HealthController {
    private final JdbcTemplate db;
    public HealthController(JdbcTemplate db) { this.db = db; }
    @GetMapping("/health") public Map<String,String> health() { return Map.of("status","ok"); }
    @GetMapping("/ready") public ResponseEntity<?> ready() {
        try {
            if (!Integer.valueOf(1).equals(db.queryForObject("select count(*) from job_leases where name = 'reminders'", Integer.class)))
                throw new IllegalStateException("Schema is not ready");
            return ResponseEntity.ok(Map.of("status", "ready"));
        } catch (Exception e) {
            return ResponseEntity.status(503).body(Map.of("status", "unavailable", "message", "AnkiDSA is starting. Please retry shortly."));
        }
    }
}
