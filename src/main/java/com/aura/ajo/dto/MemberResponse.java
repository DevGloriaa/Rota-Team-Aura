package com.aura.ajo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberResponse {

    private UUID id;
    private UUID groupId;
    private String name;
    private String email;
    private String phone;
    private String kycStatus;
    private Integer rotationPosition;
    private int trustScore;
    private boolean hasCollected;
    private String payoutAccountNumber;
    private String payoutBankCode;
    private String payoutAccountName;
    private java.math.BigDecimal owedAmount;
    private VirtualAccountResponse virtualAccount;
    private LocalDateTime createdAt;
}