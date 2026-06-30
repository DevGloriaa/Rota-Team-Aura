package com.aura.ajo.service;

public interface DefaultDetectionService {

    /**
     * Scans all PENDING contributions whose fixed periodEnd has passed and marks
     * them MISSED. No grace window — the period schedule is anchored to group.startDate
     * and is immutable:
     *
     *   PENDING  →  MISSED  when periodEnd < today
     *
     * When a contribution reaches MISSED, the owning group is flagged atRisk=true,
     * the member's trust score is recalculated, and a PeriodEndPayoutEvent is
     * published for the expired (group, cycle) pair so the payout fires after commit.
     *
     * This method is idempotent — MISSED contributions are never re-penalised.
     */
    void runDefaultDetection();
}