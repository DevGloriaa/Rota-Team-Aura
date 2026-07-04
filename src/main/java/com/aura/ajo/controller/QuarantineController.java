package com.aura.ajo.controller;

import com.aura.ajo.dto.ApiResponse;
import com.aura.ajo.dto.QuarantinedPaymentResponse;
import com.aura.ajo.dto.ResolveQuarantineRequest;
import com.aura.ajo.service.QuarantineService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Quarantine")
public class QuarantineController {

    private final QuarantineService quarantineService;

    @Operation(summary = "List quarantined payments for a group")
    @GetMapping("/groups/{groupId}/quarantine")
    public ResponseEntity<ApiResponse<List<QuarantinedPaymentResponse>>> getQuarantinedPayments(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(quarantineService.getQuarantinedPayments(groupId)));
    }

    @Operation(summary = "Resolve a quarantined payment")
    @PostMapping("/quarantine/{quarantineId}/resolve")
    public ResponseEntity<ApiResponse<QuarantinedPaymentResponse>> resolve(
            @PathVariable UUID quarantineId,
            @Valid @RequestBody ResolveQuarantineRequest request) {
        return ResponseEntity.ok(
                ApiResponse.ok("Quarantined payment resolved", quarantineService.resolve(quarantineId, request)));
    }
}
