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
public class GroupResponse {

    private UUID id;
    private String name;
    private BigDecimal contributionAmount;
    private String frequency;
    private String status;
    private int currentCycle;
    private int totalCycles;
    private boolean rotationLocked;
    private long memberCount;
    private String callbackUrl;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}