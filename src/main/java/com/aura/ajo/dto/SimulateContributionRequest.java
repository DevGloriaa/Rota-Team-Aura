package com.aura.ajo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Development-only: drives the test endpoint that injects a synthetic
 * payment_success webhook so the contribution and ledger flows can be
 * exercised without a live Nomba-to-bank transfer.
 */
@Data
@NoArgsConstructor
public class SimulateContributionRequest {

    @Schema(description = "Group to credit the simulated contribution to.")
    @NotNull
    private UUID groupId;

    @Schema(description = "Member the simulated contribution is attributed to.")
    @NotNull
    private UUID memberId;

    @Schema(description = "Amount to simulate as paid in.")
    @NotNull
    @Positive
    private BigDecimal amount;

    @Schema(required = false, description = "Optional. Supply the same value twice to exercise webhook idempotency. Auto-generated UUID if omitted.")
    private String requestId;
}