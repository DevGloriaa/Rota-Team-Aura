package com.aura.ajo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupHealthResponse {

    private UUID groupId;
    private String groupName;
    private String status;
    /** True when any member has a MISSED contribution — signals the group is at risk. */
    private boolean atRisk;
    private int currentCycle;
    private int totalCycles;
    private BigDecimal poolBalance;
    private BigDecimal contributionAmount;
    /** UUID of the member who will receive the next payout (null if group is not ACTIVE). */
    private UUID nextRecipientId;
    private String nextRecipientName;
    private List<MemberHealthEntry> members;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MemberHealthEntry {
        private UUID memberId;
        private String memberName;
        private Integer rotationPosition;
        private int trustScore;
        private boolean hasCollected;
        /** Status of this member's contribution for the current cycle, or "NO_RECORD". */
        private String currentCycleContributionStatus;
        private BigDecimal currentCycleAmountReceived;
        private LocalDate currentCyclePeriodEnd;
    }
}