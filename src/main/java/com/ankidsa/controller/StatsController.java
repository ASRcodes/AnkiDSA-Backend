package com.ankidsa.controller;

import com.ankidsa.config.AuthUtil;
import com.ankidsa.dto.StatsResponse;
import com.ankidsa.service.StatsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
@Tag(name = "Stats", description = "Dashboard statistics")
@SecurityRequirement(name = "bearerAuth")
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    @Operation(summary = "Get dashboard statistics")
    public ResponseEntity<StatsResponse> getStats() {
        Long userId = AuthUtil.getCurrentUserId();
        StatsResponse stats = statsService.getStats(userId);
        return ResponseEntity.ok(stats);
    }
}
