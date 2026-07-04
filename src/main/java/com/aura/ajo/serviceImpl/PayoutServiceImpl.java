package com.aura.ajo.serviceImpl;

import com.aura.ajo.config.NombaProperties;
import com.aura.ajo.dto.NombaBankTransferRequest;
import com.aura.ajo.dto.NombaBankTransferResponse;
import com.aura.ajo.dto.PayoutResponse;
import com.aura.ajo.entity.*;
import com.aura.ajo.enums.*;
import com.aura.ajo.exception.AppException;
import com.aura.ajo.repository.*;
import com.aura.ajo.service.CycleFundedCheckEvent;
import com.aura.ajo.service.NombaService;
import com.aura.ajo.service.NotificationService;
import com.aura.ajo.service.PayoutService;
import com.aura.ajo.service.PeriodEndPayoutEvent;
import com.aura.ajo.service.TrustScoringService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class PayoutServiceImpl implements PayoutService {

    private final SavingsGroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final PayoutRepository payoutRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final ContributionRepository contributionRepository;
    private final NombaService nombaService;
    private final NombaProperties nombaProperties;
    private final TrustScoringService trustScoringService;
    private final NotificationService notificationService;
    private final PayoutFailureRecorder failureRecorder;

    // ── Event listeners — both fire AFTER the triggering transaction commits ──

    /**
     * Fires after each committed contribution. Executes the payout ONLY if the cycle
     * is fully funded (all members PAID) — the early-payout path.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onCycleFundedCheck(CycleFundedCheckEvent event) {
        try {
            doCheckAndTrigger(event.groupId(), event.cycleNumber());
        } catch (Exception e) {
            log.error("Auto-payout (funded check) failed for group={} cycle={}: {}",
                    event.groupId(), event.cycleNumber(), e.getMessage(), e);
            failureRecorder.record(event.groupId(), event.cycleNumber(), e.getMessage());
        }
    }

    /**
     * Fires when the detection job marks contributions MISSED for an expired cycle.
     * Executes the payout with whatever is in the pool — the period-end path.
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void onPeriodEndPayout(PeriodEndPayoutEvent event) {
        try {
            triggerPayoutAtPeriodEnd(event.groupId(), event.cycleNumber());
        } catch (Exception e) {
            log.error("Auto-payout (period-end) failed for group={} cycle={}: {}",
                    event.groupId(), event.cycleNumber(), e.getMessage(), e);
            failureRecorder.record(event.groupId(), event.cycleNumber(), e.getMessage());
        }
    }

    // ── PayoutService interface ───────────────────────────────────────────────

    @Override
    @Transactional
    public void checkAndTriggerPayout(UUID groupId, int cycleNumber) {
        doCheckAndTrigger(groupId, cycleNumber);
    }

    @Override
    @Transactional
    public void triggerPayoutForCycle(UUID groupId, int cycleNumber) {
        SavingsGroup group = findGroupScoped(groupId);

        if (group.getStatus() != GroupStatus.ACTIVE) {
            throw AppException.badRequest("GROUP_NOT_ACTIVE",
                    "Payout can only be triggered for an ACTIVE group");
        }
        if (payoutRepository.existsByGroupAndCycleNumberAndStatusNot(group, cycleNumber, PayoutStatus.FAILED)) {
            throw AppException.conflict("PAYOUT_ALREADY_EXECUTED",
                    "Payout for cycle " + cycleNumber + " has already been executed");
        }

        doExecutePayout(group, cycleNumber);
    }

    @Override
    @Transactional
    public void triggerPayoutAtPeriodEnd(UUID groupId, int cycleNumber) {
        SavingsGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> AppException.notFound("Group", groupId));

        if (group.getStatus() != GroupStatus.ACTIVE) {
            return;
        }
        if (payoutRepository.existsByGroupAndCycleNumberAndStatusNot(group, cycleNumber, PayoutStatus.FAILED)) {
            log.info("Period-end payout skipped — already executed for group={} cycle={}",
                    groupId, cycleNumber);
            return;
        }

        doExecutePayout(group, cycleNumber);
    }

    @Override
    @Transactional(readOnly = true)
    public List<PayoutResponse> getPayoutHistory(UUID groupId) {
        SavingsGroup group = findGroupScoped(groupId);
        return payoutRepository.findByGroupOrderByCycleNumberAsc(group)
                .stream()
                .map(this::toPayoutResponse)
                .toList();
    }

    // ── Private implementation ─────────────────────────────────────────────────

    /** Early-payout path: only fires when all members have paid for the cycle. */
    private void doCheckAndTrigger(UUID groupId, int cycleNumber) {
        SavingsGroup group = groupRepository.findById(groupId)
                .orElseThrow(() -> AppException.notFound("Group", groupId));

        if (group.getStatus() != GroupStatus.ACTIVE) {
            return;
        }
        if (payoutRepository.existsByGroupAndCycleNumberAndStatusNot(group, cycleNumber, PayoutStatus.FAILED)) {
            log.info("Payout already recorded for group={} cycle={} — skipping", groupId, cycleNumber);
            return;
        }

        long totalMembers = memberRepository.countByGroup(group);
        long paidCount = contributionRepository.countByGroupAndCycleNumberAndStatus(
                group, cycleNumber, ContributionStatus.PAID);

        if (totalMembers == 0 || paidCount < totalMembers) {
            log.debug("Cycle {} of group={} not yet fully funded ({}/{}) — waiting",
                    cycleNumber, groupId, paidCount, totalMembers);
            return;
        }

        log.info("Cycle {} of group={} fully funded ({}/{}) — executing early payout",
                cycleNumber, groupId, paidCount, totalMembers);
        doExecutePayout(group, cycleNumber);
    }

    /**
     * Executes the payout for a cycle in a single atomic unit.
     *
     * Payout amount = actual pool balance (not a fixed formula).
     * Netting: if the recipient has a PENDING or MISSED contribution for this cycle,
     * their debt (amountExpected) is deducted from the cash transfer. The pool retains
     * the debt amount as residual balance for the next cycle.
     *
     * Recipient is resolved exclusively from the locked rotation — custody-free by design.
     */
    private void doExecutePayout(SavingsGroup group, int cycleNumber) {
        UUID groupId = group.getId();

        // 1. Recipient from locked rotation — never from any request field
        Member recipient = memberRepository
                .findByGroupAndRotationPosition(group, cycleNumber)
                .orElseThrow(() -> AppException.notFound(
                        "Member at rotation position " + cycleNumber + " in group", groupId));

        if (recipient.getPayoutAccountNumber() == null || recipient.getPayoutBankCode() == null
                || recipient.getPayoutAccountName() == null) {
            throw AppException.badRequest("MISSING_PAYOUT_DESTINATION",
                    "Recipient member " + recipient.getId() + " has no verified payout destination");
        }

        // 2. Actual pool balance — this is the gross payout amount
        BigDecimal poolBalance = computePoolBalance(group);
        if (poolBalance.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Pool balance is zero or negative for group={} cycle={} — skipping payout",
                    groupId, cycleNumber);
            return;
        }

        // 3. Netting: if the recipient owes a contribution for this cycle, deduct it
        BigDecimal recipientDebt = contributionRepository
                .findByMemberAndGroupAndCycleNumber(recipient, group, cycleNumber)
                .filter(c -> c.getStatus() == ContributionStatus.PENDING
                        || c.getStatus() == ContributionStatus.MISSED)
                .map(Contribution::getAmountExpected)
                .orElse(BigDecimal.ZERO);

        BigDecimal netTransferAmount = poolBalance.subtract(recipientDebt);
        if (netTransferAmount.compareTo(BigDecimal.ZERO) <= 0) {
            log.warn("Net payout is zero after netting for group={} cycle={} (pool={} debt={}) — skipping",
                    groupId, cycleNumber, poolBalance, recipientDebt);
            return;
        }

        // 4. Deterministic idempotency key — not derived from any caller input
        String merchantTxRef = "PAYOUT-" + groupId + "-CYC-" + cycleNumber;

        // 5. Execute Nomba interbank transfer (net amount)
        NombaBankTransferRequest transferReq = NombaBankTransferRequest.builder()
                .amount(netTransferAmount.toPlainString())
                .accountNumber(recipient.getPayoutAccountNumber())
                .accountName(recipient.getPayoutAccountName())
                .bankCode(recipient.getPayoutBankCode())
                .senderName(nombaProperties.getApi().getSenderName())
                .narration(group.getName() + " savings payout — cycle " + cycleNumber)
                .merchantTxRef(merchantTxRef)
                .build();

        NombaBankTransferResponse transferResp = nombaService.performBankTransfer(transferReq);
        assertNombaSuccess(transferResp.getCode(),
                "performBankTransfer(group=" + groupId + ",cycle=" + cycleNumber + ")");

        String nombaTransactionId = transferResp.getData() != null
                ? transferResp.getData().getId() : null;

        // 6. Record Payout (UNIQUE on (group_id, cycle_number) is last-resort guard)
        Payout payout = new Payout();
        payout.setGroup(group);
        payout.setRecipientMember(recipient);
        payout.setCycleNumber(cycleNumber);
        payout.setAmount(netTransferAmount);
        payout.setStatus(PayoutStatus.COMPLETED);
        payout.setNombaTransactionId(nombaTransactionId);
        payout.setMerchantTxRef(merchantTxRef);
        payout.setExecutedAt(LocalDateTime.now());
        payoutRepository.save(payout);

        // 7. Write DEBIT to ledger (net amount — pool retains residual debt balance)
        BigDecimal newBalance = poolBalance.subtract(netTransferAmount);
        LedgerEntry debit = new LedgerEntry();
        debit.setGroup(group);
        debit.setMember(recipient);
        debit.setType(LedgerEntryType.DEBIT);
        debit.setAmount(netTransferAmount);
        debit.setBalanceAfter(newBalance);
        debit.setTransactionReference(merchantTxRef);
        debit.setSource("PAYOUT:CYC-" + cycleNumber);
        ledgerEntryRepository.save(debit);

        // 8. Mark recipient as collected
        recipient.setHasCollected(true);
        memberRepository.save(recipient);

        // 9. Advance group: next cycle or close
        int nextCycle = cycleNumber + 1;
        if (cycleNumber >= group.getTotalCycles()) {
            group.setStatus(GroupStatus.COMPLETED);
            log.info("Group {} completed all {} cycles", groupId, group.getTotalCycles());
        } else {
            group.setCurrentCycle(nextCycle);

            // Fixed schedule anchored to group.startDate — never shifts regardless of payout timing
            int dpc = daysPerCycle(group.getFrequency());
            LocalDate startDate = group.getStartDate();
            LocalDate nextPeriodStart = startDate.plusDays((long)(nextCycle - 1) * dpc);
            LocalDate nextPeriodEnd   = startDate.plusDays((long) nextCycle * dpc);

            List<Member> allMembers = memberRepository.findByGroupOrderByRotationPositionAsc(group);
            for (Member m : allMembers) {
                Contribution nc = new Contribution();
                nc.setMember(m);
                nc.setGroup(group);
                nc.setCycleNumber(nextCycle);
                nc.setAmountExpected(group.getContributionAmount());
                nc.setPeriodStart(nextPeriodStart);
                nc.setPeriodEnd(nextPeriodEnd);
                nc.setStatus(ContributionStatus.PENDING);
                contributionRepository.save(nc);
            }
        }
        groupRepository.save(group);

        // 10. Recalculate trust scores for all group members after cycle closes
        List<Member> membersForScoring = memberRepository.findByGroupOrderByRotationPositionAsc(group);
        membersForScoring.forEach(trustScoringService::recalculateTrustScore);

        // 11. Fire CYCLE_FUNDED outbound notification (idempotent — deduped by notification service)
        notificationService.fireCycleFunded(group, cycleNumber, netTransferAmount);

        log.info("Payout executed: group={} cycle={} recipient={} pool={} debt={} netTransfer={} poolAfter={}",
                groupId, cycleNumber, recipient.getId(),
                poolBalance, recipientDebt, netTransferAmount, newBalance);
    }

    private static int daysPerCycle(Frequency frequency) {
        return switch (frequency) {
            case DAILY   -> 1;
            case WEEKLY  -> 7;
            case MONTHLY -> 30;
        };
    }

    private BigDecimal computePoolBalance(SavingsGroup group) {
        BigDecimal credits = ledgerEntryRepository
                .sumByGroupIdAndType(group.getId(), LedgerEntryType.CREDIT)
                .orElse(BigDecimal.ZERO);
        BigDecimal debits = ledgerEntryRepository
                .sumByGroupIdAndType(group.getId(), LedgerEntryType.DEBIT)
                .orElse(BigDecimal.ZERO);
        return credits.subtract(debits);
    }

    /**
     * Loads a group scoped to the authenticated integrator when a security context is present,
     * falling back to an unscoped lookup for internal paths (event listeners, period-end jobs)
     * that run outside any HTTP request.
     */
    private SavingsGroup findGroupScoped(UUID groupId) {
        UUID integratorId = currentIntegratorId();
        if (integratorId != null) {
            return groupRepository.findByIdAndIntegratorId(groupId, integratorId)
                    .orElseThrow(() -> AppException.notFound("Group", groupId));
        }
        return groupRepository.findById(groupId)
                .orElseThrow(() -> AppException.notFound("Group", groupId));
    }

    private static UUID currentIntegratorId() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getPrincipal() instanceof Integrator integrator) {
            return integrator.getId();
        }
        return null;
    }

    private static void assertNombaSuccess(String code, String operation) {
        if (!"00".equals(code)) {
            throw new AppException("NOMBA_ERROR",
                    "Nomba returned non-success code " + code + " for: " + operation,
                    HttpStatus.BAD_GATEWAY);
        }
    }

    private PayoutResponse toPayoutResponse(Payout payout) {
        Member recipient = payout.getRecipientMember();
        return PayoutResponse.builder()
                .id(payout.getId())
                .groupId(payout.getGroup().getId())
                .cycleNumber(payout.getCycleNumber())
                .recipientMemberId(recipient.getId())
                .recipientMemberName(recipient.getName())
                .recipientAccountNumber(recipient.getPayoutAccountNumber())
                .recipientBankCode(recipient.getPayoutBankCode())
                .amount(payout.getAmount())
                .status(payout.getStatus().name())
                .nombaTransactionId(payout.getNombaTransactionId())
                .merchantTxRef(payout.getMerchantTxRef())
                .executedAt(payout.getExecutedAt())
                .createdAt(payout.getCreatedAt())
                .build();
    }
}