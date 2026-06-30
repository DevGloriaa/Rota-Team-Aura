package com.aura.ajo.service;

import com.aura.ajo.entity.Member;
import com.aura.ajo.entity.SavingsGroup;

import java.math.BigDecimal;
import java.time.LocalDate;

public interface NotificationService {

    /** Fires when a member's contribution window closes in N days. Idempotent per member/cycle. */
    void fireContributionDueSoon(SavingsGroup group, int cycleNumber, Member member, LocalDate periodEnd);

    /** Fires on the last day of a member's contribution window. Idempotent per member/cycle. */
    void fireContributionDueNow(SavingsGroup group, int cycleNumber, Member member, LocalDate periodEnd);

    /** Fires when a member's PENDING contribution is marked MISSED. Idempotent per member/cycle. */
    void fireContributionMissed(SavingsGroup group, int cycleNumber, Member member, BigDecimal amountMissed);

    /** Fires once the cycle payout executes (early or period-end). Idempotent per group/cycle. */
    void fireCycleFunded(SavingsGroup group, int cycleNumber, BigDecimal payoutAmount);
}