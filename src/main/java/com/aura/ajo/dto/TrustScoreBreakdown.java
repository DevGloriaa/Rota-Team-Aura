package com.aura.ajo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Full breakdown of a member's trust score, showing every component so the
 * formula is transparent and explainable to the member and group admin.
 *
 * Score formula (all components sum to [0, 100]):
 *   coldStartBase       KYC depth: VERIFIED=50, PENDING=25, REJECTED=0
 *   + onTimeScore       onTimePaidCount / totalResolved * 30            (0–30)
 *   + experienceBonus   min(completedCycles * 3, 15)                    (0–15)
 *   - latenessDeduction ceil(avgDaysLate * 0.5)
 *   - missedPenalty     missedCount * 15
 *   Clamped to [0, 100].
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TrustScoreBreakdown {

    private UUID memberId;
    private String memberName;
    private String kycStatus;
    private int totalScore;

    // ── Cold start (KYC depth) ─────────────────────────────────────────────────
    private int coldStartBase;
    private String coldStartReason;

    // ── On-time rate (0–30 points) ────────────────────────────────────────────
    /** Contributions resolved (not PENDING) across all groups for this email. */
    private int totalResolvedContributions;
    /** Contributions paid on or before their dueDate. */
    private int onTimePaidCount;
    /** onTimePaidCount / totalResolvedContributions; 0 if no history. */
    private double onTimeRate;
    /** onTimeRate * 30, rounded. */
    private int onTimeScore;

    // ── Experience bonus (0–15 points) ───────────────────────────────────────
    /** Number of groups where this member has collected (hasCollected=true). */
    private long completedCycles;
    /** min(completedCycles * 3, 15). */
    private int experienceBonus;

    // ── Lateness deduction ────────────────────────────────────────────────────
    /** Average days between dueDate and paidAt for contributions paid late. */
    private double avgDaysLate;
    /** ceil(avgDaysLate * 0.5). */
    private int latenessDeduction;

    // ── Default penalty ───────────────────────────────────────────────────────
    /** Contributions that reached MISSED status (across all groups). */
    private long missedCount;
    /** missedCount * 15. */
    private int missedPenalty;

    // ── Raw counts for dashboard ──────────────────────────────────────────────
    private int latePaidCount;
}