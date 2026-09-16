package com.ankidsa.controller;

import com.ankidsa.config.AuthUtil;
import com.ankidsa.dto.LogProblemRequest;
import com.ankidsa.dto.ProblemResponse;
import com.ankidsa.dto.ReviewRequest;
import com.ankidsa.dto.ReviewResponse;
import com.ankidsa.service.ProblemService;
import com.ankidsa.service.SrsService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * UPDATED PROBLEM CONTROLLER — Week 2 version.
 *
 * KEY CHANGE: No more @RequestParam Long userId.
 * userId now comes from the JWT token via AuthUtil.getCurrentUserId().
 *
 * The client sends:
 *   GET /api/problems/queue
 *   Headers: Authorization: Bearer eyJhbGci...
 *
 * Instead of:
 *   GET /api/problems/queue?userId=1  (insecure!)
 *
 * @SecurityRequirement tells Swagger UI to show the "Authorize" button
 * and include the Bearer token in test requests.
 */
@RestController
@RequestMapping("/api/problems")
@RequiredArgsConstructor
@Tag(name = "Problems", description = "Log and manage DSA problems")
@SecurityRequirement(name = "bearerAuth")
public class ProblemController {

    private final ProblemService problemService;
    private final SrsService srsService;

    @PostMapping
    @Operation(summary = "Log a new DSA problem")
    public ResponseEntity<ProblemResponse> logProblem(
            @Valid @RequestBody LogProblemRequest request) {
        // No more @RequestParam userId — extracted from JWT
        Long userId = AuthUtil.getCurrentUserId();
        ProblemResponse response = problemService.logProblem(request, userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/queue")
    @Operation(summary = "Get today's review queue")
    public ResponseEntity<List<ProblemResponse>> getDailyQueue() {
        Long userId = AuthUtil.getCurrentUserId();
        List<ProblemResponse> queue = problemService.getDailyQueue(userId);
        return ResponseEntity.ok(queue);
    }

    @GetMapping
    @Operation(summary = "Get all logged problems")
    public ResponseEntity<List<ProblemResponse>> getAllProblems() {
        Long userId = AuthUtil.getCurrentUserId();
        List<ProblemResponse> problems = problemService.getAllProblems(userId);
        return ResponseEntity.ok(problems);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get a specific problem by ID")
    public ResponseEntity<ProblemResponse> getProblem(@PathVariable Long id) {
        ProblemResponse problem = problemService.getProblem(id, AuthUtil.getCurrentUserId());
        return ResponseEntity.ok(problem);
    }

    @PostMapping("/review")
    @Operation(summary = "Submit a review for a problem")
    public ResponseEntity<ReviewResponse> submitReview(
            @Valid @RequestBody ReviewRequest request) {
        Long userId = AuthUtil.getCurrentUserId();
        ReviewResponse response = srsService.processReview(request, userId);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    public ProblemResponse update(@PathVariable Long id, @Valid @RequestBody LogProblemRequest request) {
        return problemService.update(id, AuthUtil.getCurrentUserId(), request);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable Long id) {
        problemService.delete(id, AuthUtil.getCurrentUserId());
    }
}
