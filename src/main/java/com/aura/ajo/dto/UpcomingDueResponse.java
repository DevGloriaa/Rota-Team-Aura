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
public class UpcomingDueResponse {

    private UUID groupId;
    private LocalDate asOfDate;
    private List<DueEntry> upcomingDues;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DueEntry {
        private UUID memberId;
        private String memberName;
        private int cycleNumber;
        private BigDecimal amountExpected;
        private LocalDate periodStart;
        private LocalDate periodEnd;
        private String status;
        /** Calendar days from today until periodEnd. Negative means overdue. */
        private long daysUntilDue;
    }
}