package com.aura.ajo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MemberStatementResponse {

    private UUID groupId;
    private UUID memberId;
    private String memberName;
    private Integer rotationPosition;
    private boolean hasCollected;
    private int trustScore;
    private BigDecimal owedAmount;

    /** Sum of amountExpected across every cycle recorded for this member in this group. */
    private BigDecimal totalExpected;
    /** Sum of amountReceived across every PAID contribution. */
    private BigDecimal totalPaid;

    private List<ContributionEntry> contributions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ContributionEntry {
        private int cycleNumber;
        private BigDecimal amountExpected;
        private BigDecimal amountReceived;
        private String status;
        private LocalDateTime paidAt;
        private LocalDate periodStart;
        private LocalDate periodEnd;
    }
}
