package com.aura.ajo.service;

import com.aura.ajo.dto.TrustScoreBreakdown;
import com.aura.ajo.entity.Member;

import java.util.UUID;

public interface TrustScoringService {

    /**
     * Recalculates and persists the trustScore for a member based on their full
     * contribution history (across all groups), KYC depth, and default history.
     *
     * IMPORTANT: this method updates Member.trustScore only — it never touches
     * Member.rotationPosition. Rotation order for ACTIVE groups is locked at
     * activation and is immutable. Updated scores affect only FORMING groups
     * (at their activation sort) and the member's standing in future groups.
     */
    void recalculateTrustScore(Member member);

    /** Returns the full score breakdown for a member without mutating anything. */
    TrustScoreBreakdown getScoreBreakdown(UUID memberId);
}