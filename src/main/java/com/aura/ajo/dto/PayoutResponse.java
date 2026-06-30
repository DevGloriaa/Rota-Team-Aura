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
public class PayoutResponse {

    private UUID id;
    private UUID groupId;
    private int cycleNumber;
    private UUID recipientMemberId;
    private String recipientMemberName;
    private String recipientAccountNumber;
    private String recipientBankCode;
    private BigDecimal amount;
    private String status;
    private String nombaTransactionId;
    private String merchantTxRef;
    private LocalDateTime executedAt;
    private LocalDateTime createdAt;
}