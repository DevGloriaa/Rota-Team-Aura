package com.aura.ajo.service;

import com.aura.ajo.dto.PayoutResponse;

import java.util.List;
import java.util.UUID;

public interface PayoutService {

    /**
     * Checks whether the given cycle is fully funded (all members PAID) and, if so,
     * executes the payout in the same transaction. Called automatically by the
     * CycleFundedCheckEvent listener after every committed contribution.
     */
    void checkAndTriggerPayout(UUID groupId, int cycleNumber);

    /**
     * Force-executes the payout for a specific cycle without re-checking the funding
     * count. Throws if payout has already been executed or the group is not ACTIVE.
     * Intended for manual retry and testing only.
     */
    void triggerPayoutForCycle(UUID groupId, int cycleNumber);

    /**
     * Executes the payout at period-end regardless of funding level. Uses the actual
     * pool balance as the payout amount, applying the netting rule if the recipient
     * also has an unpaid contribution for this cycle. Skips silently if payout was
     * already executed (idempotent). Called by the PeriodEndPayoutEvent listener.
     */
    void triggerPayoutAtPeriodEnd(UUID groupId, int cycleNumber);

    /** Returns the full payout history for a group, ordered by cycle number ascending. */
    List<PayoutResponse> getPayoutHistory(UUID groupId);
}