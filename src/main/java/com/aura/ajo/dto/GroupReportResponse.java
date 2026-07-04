package com.aura.ajo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupReportResponse {

    private UUID groupId;
    private String groupName;
    private String status;
    private int currentCycle;
    private int totalCycles;

    /** Derived from the ledger: total CREDITs minus total DEBITs. */
    private BigDecimal poolBalance;

    private List<MemberFundingEntry> currentCycleFunding;
    private List<PayoutResponse> payoutHistory;
    private List<RotationEntry> rotationOrder;

    /** Member at rotationPosition == currentCycle — null if the group isn't ACTIVE yet. */
    private UUID nextToCollectMemberId;
    private String nextToCollectMemberName;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberFundingEntry {
        private UUID memberId;
        private String memberName;
        /** PENDING, PAID, MISSED, or NO_RECORD if no contribution exists yet for this cycle. */
        private String status;
        private BigDecimal amount;
    }
}
