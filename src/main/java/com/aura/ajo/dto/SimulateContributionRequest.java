package com.aura.ajo.dto;

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

    @NotNull
    private UUID groupId;

    @NotNull
    private UUID memberId;

    @NotNull
    @Positive
    private BigDecimal amount;

    /**
     * Optional. Supply the same value twice to exercise webhook idempotency.
     * Auto-generated UUID if omitted.
     */
    private String requestId;
}