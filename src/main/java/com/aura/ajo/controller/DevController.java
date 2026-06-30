package com.aura.ajo.controller;

import com.aura.ajo.dto.ApiResponse;
import com.aura.ajo.dto.SimulateContributionRequest;
import com.aura.ajo.service.DefaultDetectionService;
import com.aura.ajo.service.GroupService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Dev-only test endpoints. Only registered when the "dev" Spring profile is active.
 * Start the application with --spring.profiles.active=dev (or SPRING_PROFILES_ACTIVE=dev).
 * These routes are completely absent in any other profile.
 *
 * curl examples:
 *
 *   # Simulate a contribution (supply same requestId twice to test idempotency)
 *   curl -s -X POST localhost:8080/api/v1/test/simulate-contribution \
 *     -H 'Content-Type: application/json' \
 *     -d '{"groupId":"{groupId}","memberId":"{memberId}","amount":10000,"requestId":"test-req-1"}'
 *
 *   # Manually fire the default-detection job
 *   curl -s -X POST localhost:8080/api/v1/test/run-default-detection
 */
@RestController
@RequestMapping("/api/v1/test")
@RequiredArgsConstructor
@Profile("dev")
public class DevController {

    private final GroupService groupService;
    private final DefaultDetectionService defaultDetectionService;

    @PostMapping("/run-default-detection")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerDefaultDetection() {
        defaultDetectionService.runDefaultDetection();
        return ResponseEntity.ok(ApiResponse.ok(Map.of("message", "Default detection job completed")));
    }

    @PostMapping("/simulate-contribution")
    public ResponseEntity<ApiResponse<Map<String, Object>>> simulateContribution(
            @Valid @RequestBody SimulateContributionRequest request) {
        Map<String, Object> result = groupService.simulateContribution(request);
        return ResponseEntity.ok(ApiResponse.ok("Contribution simulated", result));
    }
}
