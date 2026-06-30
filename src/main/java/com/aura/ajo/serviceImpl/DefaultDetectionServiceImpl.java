package com.aura.ajo.serviceImpl;

import com.aura.ajo.config.SchedulerProperties;
import com.aura.ajo.entity.Contribution;
import com.aura.ajo.entity.Member;
import com.aura.ajo.entity.SavingsGroup;
import com.aura.ajo.enums.ContributionStatus;
import com.aura.ajo.repository.ContributionRepository;
import com.aura.ajo.repository.MemberRepository;
import com.aura.ajo.repository.SavingsGroupRepository;
import com.aura.ajo.service.DefaultDetectionService;
import com.aura.ajo.service.NotificationService;
import com.aura.ajo.service.PeriodEndPayoutEvent;
import com.aura.ajo.service.TrustScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class DefaultDetectionServiceImpl implements DefaultDetectionService {

    private final ContributionRepository contributionRepository;
    private final SavingsGroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final TrustScoringService trustScoringService;
    private final NotificationService notificationService;
    private final SchedulerProperties schedulerProperties;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Period-based model (Model A): no grace window, no LATE state.
     *
     * Each run does three passes (in order):
     *   1. PENDING → MISSED  for contributions whose periodEnd < today
     *   2. DUE_NOW notice     for PENDING contributions whose periodEnd == today
     *   3. DUE_SOON notice    for PENDING contributions whose periodEnd == today + dueSoonDays
     *
     * After all defaults are recorded, one PeriodEndPayoutEvent is published per
     * distinct expired (group, cycle) pair — fires AFTER this TX commits.
     * Idempotent: MISSED contributions are never re-penalised; notifications are
     * deduped by the notification service.
     */
    @Override
    @Scheduled(cron = "${savings.scheduler.default-detection.cron:0 */15 * * * *}")
    @Transactional
    public void runDefaultDetection() {
        if (!schedulerProperties.getDefaultDetection().isEnabled()) {
            log.debug("Default detection disabled — skipping");
            return;
        }

        LocalDate today = LocalDate.now();
        int dueSoonDays = schedulerProperties.getDefaultDetection().getDueSoonDays();

        // ── Pass 1: PENDING → MISSED (periodEnd strictly before today) ─────────
        List<Contribution> toMiss = contributionRepository
                .findByStatusAndPeriodEndBefore(ContributionStatus.PENDING, today);

        Map<UUID, Set<Integer>> expiredCycles = new LinkedHashMap<>();

        for (Contribution c : toMiss) {
            c.setStatus(ContributionStatus.MISSED);
            contributionRepository.save(c);

            // Accumulate the gap as tracked debt on the member record
            Member member = c.getMember();
            member.setOwedAmount(member.getOwedAmount().add(c.getAmountExpected()));
            memberRepository.save(member);

            SavingsGroup group = c.getGroup();
            if (!group.isAtRisk()) {
                group.setAtRisk(true);
                groupRepository.save(group);
                log.warn("Group {} flagged AT RISK — MISSED contribution by member={} cycle={}",
                        group.getId(), member.getId(), c.getCycleNumber());
            }

            trustScoringService.recalculateTrustScore(member);

            notificationService.fireContributionMissed(
                    group, c.getCycleNumber(), member, c.getAmountExpected());

            expiredCycles
                    .computeIfAbsent(group.getId(), k -> new LinkedHashSet<>())
                    .add(c.getCycleNumber());

            log.info("Contribution {} PENDING→MISSED: member={} group={} cycle={} periodEnd={} owedNow={}",
                    c.getId(), member.getId(), group.getId(),
                    c.getCycleNumber(), c.getPeriodEnd(), member.getOwedAmount());
        }

        // Publish period-end payout events — listeners fire AFTER this TX commits
        for (Map.Entry<UUID, Set<Integer>> entry : expiredCycles.entrySet()) {
            for (int cycleNumber : entry.getValue()) {
                eventPublisher.publishEvent(new PeriodEndPayoutEvent(entry.getKey(), cycleNumber));
            }
        }

        // ── Pass 2: DUE_NOW — PENDING contributions expiring exactly today ──────
        List<Contribution> dueToday = contributionRepository
                .findByStatusAndPeriodEnd(ContributionStatus.PENDING, today);
        for (Contribution c : dueToday) {
            notificationService.fireContributionDueNow(
                    c.getGroup(), c.getCycleNumber(), c.getMember(), c.getPeriodEnd());
        }

        // ── Pass 3: DUE_SOON — PENDING contributions expiring in N days ─────────
        List<Contribution> dueSoon = contributionRepository
                .findByStatusAndPeriodEnd(ContributionStatus.PENDING, today.plusDays(dueSoonDays));
        for (Contribution c : dueSoon) {
            notificationService.fireContributionDueSoon(
                    c.getGroup(), c.getCycleNumber(), c.getMember(), c.getPeriodEnd());
        }

        if (!toMiss.isEmpty() || !dueToday.isEmpty() || !dueSoon.isEmpty()) {
            log.info("Default detection: {} MISSED, {} DUE_NOW, {} DUE_SOON, {} payout events queued",
                    toMiss.size(), dueToday.size(), dueSoon.size(),
                    expiredCycles.values().stream().mapToInt(Set::size).sum());
        } else {
            log.debug("Default detection: nothing to do");
        }
    }
}