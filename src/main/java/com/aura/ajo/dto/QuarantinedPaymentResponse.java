package com.aura.ajo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuarantinedPaymentResponse {

    private UUID id;
    private UUID groupId;
    private String virtualAccountNumber;
    private BigDecimal amount;
    private String nombaTransactionRef;
    private String reason;
    private String status;
    private String resolutionNote;
    private LocalDateTime createdAt;
    private LocalDateTime resolvedAt;
}
