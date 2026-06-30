package com.aura.ajo.serviceImpl;

import com.aura.ajo.dto.TrustScoreBreakdown;
import com.aura.ajo.entity.Contribution;
import com.aura.ajo.entity.Member;
import com.aura.ajo.enums.ContributionStatus;
import com.aura.ajo.enums.KycStatus;
import com.aura.ajo.exception.AppException;
import com.aura.ajo.repository.ContributionRepository;
import com.aura.ajo.repository.MemberRepository;
import com.aura.ajo.service.TrustScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.OptionalDouble;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class TrustScoringServiceImpl implements TrustScoringService {

    private final MemberRepository memberRepository;
    private final ContributionRepository contributionRepository;

    // ── Score weights (documented for explainability) ─────────────────────────

    /** KYC-verified member base. Nomba returns bvn field in VA creation response. */
    private static final int BASE_KYC_VERIFIED = 50;
    /** Member whose identity is not yet confirmed by Nomba starts lower. */
    private static final int BASE_KYC_PENDING = 25;
    /** Rejected KYC — no positive base. */
    private static final int BASE_KYC_REJECTED = 0;

    /** Max points for a perfect on-time payment record. Heaviest positive factor. */
    private static final double ON_TIME_WEIGHT = 30.0;
    /** Points per completed cycle (collected payout). Capped to discourage gaming. */
    private static final int EXPERIENCE_POINTS_PER_CYCLE = 3;
    private static final int EXPERIENCE_MAX = 15;

    /**
     * Lateness multiplier: every day past dueDate costs 0.5 points (avg across late payments).
     * Mild — the real penalty comes from the MISSED weight.
     */
    private static final double LATE_PENALTY_PER_DAY = 0.5;
    /** Per-MISSED contribution penalty. Heaviest negative weight. */
    private static final int MISSED_PENALTY = 15;

    // ── Interface implementation ──────────────────────────────────────────────

    @Override
    @Transactional
    public void recalculateTrustScore(Member member) {
        TrustScoreBreakdown breakdown = buildBreakdown(member);
        int oldScore = member.getTrustScore();
        member.setTrustScore(breakdown.getTotalScore());
        memberRepository.save(member);
        log.info("Trust score recalculated for member={}: {} → {}",
                member.getId(), oldScore, breakdown.getTotalScore());
    }

    @Override
    @Transactional(readOnly = true)
    public TrustScoreBreakdown getScoreBreakdown(UUID memberId) {
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> AppException.notFound("Member", memberId));
        return buildBreakdown(member);
    }

    // ── Core calculation ──────────────────────────────────────────────────────

    private TrustScoreBreakdown buildBreakdown(Member member) {

        // ── Cold start from KYC depth ─────────────────────────────────────────
        // Signal: Nomba's bvn field in the virtual account creation response.
        // GroupServiceImpl sets kycStatus=VERIFIED when Nomba returns a non-blank BVN.
        int coldStartBase = switch (member.getKycStatus()) {
            case VERIFIED -> BASE_KYC_VERIFIED;
            case PENDING  -> BASE_KYC_PENDING;
            case REJECTED -> BASE_KYC_REJECTED;
        };
        String coldStartReason = switch (member.getKycStatus()) {
            case VERIFIED -> "BVN confirmed by Nomba (+50)";
            case PENDING  -> "Identity not yet verified (+25)";
            case REJECTED -> "KYC rejected (0)";
        };

        // ── Contribution history across ALL groups for this email ─────────────
        // Portable reputation: history is not scoped to one group.
        List<Contribution> allContributions =
                contributionRepository.findByMemberEmail(member.getEmail());

        List<Contribution> resolved = allContributions.stream()
                .filter(c -> c.getStatus() != ContributionStatus.PENDING)
                .toList();

        int totalResolved = resolved.size();

        // On time = PAID and paidAt is not after periodEnd
        List<Contribution> onTimePaid = resolved.stream()
                .filter(c -> c.getStatus() == ContributionStatus.PAID
                        && c.getPaidAt() != null && c.getPeriodEnd() != null
                        && !c.getPaidAt().toLocalDate().isAfter(c.getPeriodEnd()))
                .toList();

        // Late-but-paid = PAID and paidAt is after periodEnd
        List<Contribution> latePaid = resolved.stream()
                .filter(c -> c.getStatus() == ContributionStatus.PAID
                        && c.getPaidAt() != null && c.getPeriodEnd() != null
                        && c.getPaidAt().toLocalDate().isAfter(c.getPeriodEnd()))
                .toList();

        long missedCount = resolved.stream()
                .filter(c -> c.getStatus() == ContributionStatus.MISSED)
                .count();

        // ── On-time score ─────────────────────────────────────────────────────
        double onTimeRate = totalResolved > 0 ? (double) onTimePaid.size() / totalResolved : 0.0;
        int onTimeScore = (int) Math.round(onTimeRate * ON_TIME_WEIGHT);

        // ── Experience bonus ──────────────────────────────────────────────────
        long completedCycles = memberRepository.countByEmailAndHasCollectedTrue(member.getEmail());
        int experienceBonus = (int) Math.min(completedCycles * EXPERIENCE_POINTS_PER_CYCLE,
                EXPERIENCE_MAX);

        // ── Lateness severity ─────────────────────────────────────────────────
        OptionalDouble avgDaysLate = latePaid.stream()
                .mapToLong(c -> ChronoUnit.DAYS.between(
                        c.getPeriodEnd(), c.getPaidAt().toLocalDate()))
                .average();
        int latenessDeduction = (int) Math.ceil(avgDaysLate.orElse(0) * LATE_PENALTY_PER_DAY);

        // ── Default penalty ───────────────────────────────────────────────────
        int missedPenalty = (int) (missedCount * MISSED_PENALTY);

        // ── Composite score ───────────────────────────────────────────────────
        int raw = coldStartBase + onTimeScore + experienceBonus
                - latenessDeduction - missedPenalty;
        int finalScore = Math.max(0, Math.min(100, raw));

        return TrustScoreBreakdown.builder()
                .memberId(member.getId())
                .memberName(member.getName())
                .kycStatus(member.getKycStatus().name())
                .totalScore(finalScore)
                .coldStartBase(coldStartBase)
                .coldStartReason(coldStartReason)
                .totalResolvedContributions(totalResolved)
                .onTimePaidCount(onTimePaid.size())
                .onTimeRate(onTimeRate)
                .onTimeScore(onTimeScore)
                .completedCycles(completedCycles)
                .experienceBonus(experienceBonus)
                .avgDaysLate(avgDaysLate.orElse(0))
                .latenessDeduction(latenessDeduction)
                .missedCount(missedCount)
                .missedPenalty(missedPenalty)
                .latePaidCount(latePaid.size())
                .build();
    }
}