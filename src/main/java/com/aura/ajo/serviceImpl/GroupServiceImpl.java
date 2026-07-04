package com.aura.ajo.serviceImpl;

import com.aura.ajo.dto.AddMemberRequest;
import com.aura.ajo.dto.CreateGroupRequest;
import com.aura.ajo.dto.GroupHealthResponse;
import com.aura.ajo.dto.GroupReportResponse;
import com.aura.ajo.dto.GroupResponse;
import com.aura.ajo.dto.MemberResponse;
import com.aura.ajo.dto.MemberStatementResponse;
import com.aura.ajo.dto.NombaBankResolveRequest;
import com.aura.ajo.dto.NombaBankResolveResponse;
import com.aura.ajo.dto.NombaCreateVirtualAccountRequest;
import com.aura.ajo.dto.NombaCreateVirtualAccountResponse;
import com.aura.ajo.dto.PayoutResponse;
import com.aura.ajo.dto.ProvisionResponse;
import com.aura.ajo.dto.RotationEntry;
import com.aura.ajo.dto.SimulateContributionRequest;
import com.aura.ajo.dto.UpcomingDueResponse;
import com.aura.ajo.dto.VirtualAccountResponse;
import com.aura.ajo.entity.*;
import com.aura.ajo.enums.*;
import com.aura.ajo.exception.AppException;
import com.aura.ajo.repository.*;
import com.aura.ajo.service.GroupService;
import com.aura.ajo.service.NombaService;
import com.aura.ajo.service.PayoutService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class GroupServiceImpl implements GroupService {

    private final SavingsGroupRepository groupRepository;
    private final MemberRepository memberRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final WebhookEventRepository webhookEventRepository;
    private final ContributionRepository contributionRepository;
    private final NombaService nombaService;
    private final PayoutService payoutService;

    // -------------------------------------------------------------------------
    // Group lifecycle
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public GroupResponse createGroup(CreateGroupRequest request) {
        SavingsGroup group = new SavingsGroup();
        group.setName(request.getName());
        group.setContributionAmount(request.getContributionAmount());
        group.setFrequency(request.getFrequency());
        group.setCallbackUrl(request.getCallbackUrl());
        group.setStatus(GroupStatus.FORMING);
        group.setIntegratorId(currentIntegratorId());
        group = groupRepository.save(group);
        log.info("Created group {} ({}) for integrator={}", group.getName(), group.getId(), group.getIntegratorId());
        return toGroupResponse(group);
    }

    @Override
    @Transactional(readOnly = true)
    public List<GroupResponse> listGroups() {
        UUID integratorId = currentIntegratorId();
        if (integratorId == null) {
            throw AppException.badRequest("AUTH_REQUIRED", "Authentication required to list groups");
        }
        return groupRepository.findByIntegratorIdOrderByCreatedAtDesc(integratorId).stream()
                .map(this::toGroupResponse)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public GroupResponse getGroup(UUID groupId) {
        return toGroupResponse(findGroupOrThrow(groupId));
    }

    // -------------------------------------------------------------------------
    // Member management
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public MemberResponse addMember(UUID groupId, AddMemberRequest request) {
        SavingsGroup group = findGroupOrThrow(groupId);

        if (group.getStatus() != GroupStatus.FORMING) {
            throw AppException.badRequest("GROUP_NOT_FORMING",
                "Members can only be added while the group is in FORMING status");
        }
        if (memberRepository.existsByGroupAndEmail(group, request.getEmail())) {
            throw AppException.conflict("DUPLICATE_MEMBER",
                "A member with email " + request.getEmail() + " already exists in this group");
        }

        // Verify bank account with Nomba before persisting — locks in the destination early
        NombaBankResolveResponse resolveResp = nombaService.resolveBankAccount(
            NombaBankResolveRequest.builder()
                .accountNumber(request.getPayoutAccountNumber())
                .bankCode(request.getPayoutBankCode())
                .build());
        assertNombaSuccess(resolveResp.getCode(),
            "resolveBankAccount(member=" + request.getEmail() + ")");

        Member member = new Member();
        member.setGroup(group);
        member.setName(request.getName());
        member.setEmail(request.getEmail());
        member.setPhone(request.getPhone());
        member.setTrustScore(request.getTrustScore());
        member.setNombaAccountId(request.getNombaAccountId());
        member.setPayoutAccountNumber(request.getPayoutAccountNumber());
        member.setPayoutBankCode(request.getPayoutBankCode());
        member.setPayoutAccountName(resolveResp.getData().getAccountName());
        member = memberRepository.save(member);

        log.info("Added member {} to group {}", member.getId(), groupId);
        return toMemberResponse(member, null);
    }

    @Override
    @Transactional(readOnly = true)
    public List<MemberResponse> getMembers(UUID groupId) {
        SavingsGroup group = findGroupOrThrow(groupId);
        List<Member> members = group.isRotationLocked()
            ? memberRepository.findByGroupOrderByRotationPositionAsc(group)
            : memberRepository.findByGroupOrderByCreatedAtAsc(group);

        return members.stream()
            .map(m -> {
                VirtualAccount va = virtualAccountRepository
                    .findByMemberAndType(m, VirtualAccountType.MEMBER)
                    .orElse(null);
                return toMemberResponse(m, va);
            })
            .toList();
    }

    // -------------------------------------------------------------------------
    // Virtual account provisioning
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public ProvisionResponse provisionVirtualAccounts(UUID groupId) {
        SavingsGroup group = findGroupOrThrow(groupId);

        if (group.getStatus() != GroupStatus.FORMING) {
            throw AppException.badRequest("GROUP_NOT_FORMING",
                "Virtual accounts can only be provisioned while the group is FORMING");
        }

        List<Member> members = memberRepository.findByGroupOrderByCreatedAtAsc(group);
        if (members.isEmpty()) {
            throw AppException.badRequest("NO_MEMBERS", "Add at least one member before provisioning");
        }

        int provisioned = 0;
        List<MemberResponse> memberResponses = new ArrayList<>();

        for (Member member : members) {
            if (virtualAccountRepository.existsByMemberAndType(member, VirtualAccountType.MEMBER)) {
                VirtualAccount existing = virtualAccountRepository
                    .findByMemberAndType(member, VirtualAccountType.MEMBER).orElseThrow();
                memberResponses.add(toMemberResponse(member, existing));
                continue;
            }

            VirtualAccount va = createMemberVirtualAccount(group, member);
            memberResponses.add(toMemberResponse(member, va));
            provisioned++;
        }

        boolean poolJustProvisioned = false;
        if (!virtualAccountRepository.existsByGroupAndTypeAndMemberIsNull(group, VirtualAccountType.GROUP_POOL)) {
            createGroupPoolVirtualAccount(group);
            poolJustProvisioned = true;
        }

        log.info("Provisioned {} new VAs for group {}; pool newly provisioned: {}",
            provisioned, groupId, poolJustProvisioned);

        return ProvisionResponse.builder()
            .groupId(groupId)
            .membersProvisioned(provisioned)
            .groupPoolProvisioned(poolJustProvisioned)
            .members(memberResponses)
            .build();
    }

    private VirtualAccount createMemberVirtualAccount(SavingsGroup group, Member member) {
        String accountRef = "MBR-" + UUID.randomUUID().toString().replace("-", "");
        String accountName = toNombaAccountName(member.getName());

        NombaCreateVirtualAccountRequest req = NombaCreateVirtualAccountRequest.builder()
            .accountRef(accountRef)
            .accountName(accountName)
            .build();

        NombaCreateVirtualAccountResponse resp = nombaService.createVirtualAccount(req);
        assertNombaSuccess(resp.getCode(), "createVirtualAccount(member=" + member.getId() + ")");

        // Nomba returns bvn when the account holder is BVN-linked; treat this as identity verified.
        if (resp.getData().getBvn() != null && !resp.getData().getBvn().isBlank()) {
            member.setKycStatus(KycStatus.VERIFIED);
            memberRepository.save(member);
            log.info("KYC set to VERIFIED for member={} (Nomba returned BVN)", member.getId());
        }

        VirtualAccount va = new VirtualAccount();
        va.setGroup(group);
        va.setMember(member);
        va.setAccountRef(accountRef);
        va.setBankAccountNumber(resp.getData().getBankAccountNumber());
        va.setBankAccountName(resp.getData().getBankAccountName());
        va.setBankName(resp.getData().getBankName());
        va.setNombaAccountHolderId(resp.getData().getAccountHolderId());
        va.setType(VirtualAccountType.MEMBER);
        return virtualAccountRepository.save(va);
    }

    private VirtualAccount createGroupPoolVirtualAccount(SavingsGroup group) {
        String accountRef = "POOL-" + UUID.randomUUID().toString().replace("-", "");
        /*
         * Production hardening: per-group sub-account isolation (true custody separation) can be
         * enabled via the Nomba dashboard or Nomba's sub-account API. Not required for MVP.
         */
        String accountName = toNombaAccountName("POOL: " + group.getName());

        NombaCreateVirtualAccountRequest req = NombaCreateVirtualAccountRequest.builder()
            .accountRef(accountRef)
            .accountName(accountName)
            .build();

        NombaCreateVirtualAccountResponse resp = nombaService.createVirtualAccount(req);
        assertNombaSuccess(resp.getCode(), "createVirtualAccount(pool, group=" + group.getId() + ")");

        VirtualAccount va = new VirtualAccount();
        va.setGroup(group);
        va.setMember(null);
        va.setAccountRef(accountRef);
        va.setBankAccountNumber(resp.getData().getBankAccountNumber());
        va.setBankAccountName(resp.getData().getBankAccountName());
        va.setBankName(resp.getData().getBankName());
        va.setNombaAccountHolderId(resp.getData().getAccountHolderId());
        va.setType(VirtualAccountType.GROUP_POOL);
        return virtualAccountRepository.save(va);
    }

    // -------------------------------------------------------------------------
    // Activation — locks trust-ordered rotation
    // -------------------------------------------------------------------------

    @Override
    @Transactional
    public GroupResponse activateGroup(UUID groupId) {
        SavingsGroup group = findGroupOrThrow(groupId);

        if (group.getStatus() != GroupStatus.FORMING) {
            throw AppException.badRequest("GROUP_NOT_FORMING",
                "Only a FORMING group can be activated");
        }

        List<Member> members = memberRepository.findByGroupOrderByCreatedAtAsc(group);

        if (members.size() < 2) {
            throw AppException.badRequest("INSUFFICIENT_MEMBERS",
                "A group needs at least 2 members to activate");
        }

        boolean allProvisioned = members.stream().allMatch(m ->
            virtualAccountRepository.existsByMemberAndType(m, VirtualAccountType.MEMBER));
        if (!allProvisioned) {
            throw AppException.badRequest("ACCOUNTS_NOT_PROVISIONED",
                "All members must have virtual accounts provisioned before activation — call /provision first");
        }

        // Sort: highest trustScore first (collects soonest).
        // Equal scores: earlier join date breaks the tie (fair FIFO within same trust tier).
        // Zero / no-history members always collect last.
        members.sort(
            Comparator.comparingInt(Member::getTrustScore).reversed()
                .thenComparing(Member::getCreatedAt)
        );

        for (int i = 0; i < members.size(); i++) {
            Member m = members.get(i);
            m.setRotationPosition(i + 1);
            memberRepository.save(m);
        }

        // Anchor the cycle schedule to today — all periodStart/periodEnd values derive from this
        LocalDate startDate = LocalDate.now();
        group.setStartDate(startDate);
        group.setStatus(GroupStatus.ACTIVE);
        group.setTotalCycles(members.size());
        group.setCurrentCycle(1);
        group.setRotationLocked(true);
        group = groupRepository.save(group);

        // Create PENDING contributions for cycle 1 with fixed period window
        int dpc = daysPerCycle(group.getFrequency());
        LocalDate c1PeriodStart = startDate;
        LocalDate c1PeriodEnd   = startDate.plusDays(dpc);
        for (Member m : members) {
            Contribution c = new Contribution();
            c.setMember(m);
            c.setGroup(group);
            c.setCycleNumber(1);
            c.setAmountExpected(group.getContributionAmount());
            c.setPeriodStart(c1PeriodStart);
            c.setPeriodEnd(c1PeriodEnd);
            c.setStatus(ContributionStatus.PENDING);
            contributionRepository.save(c);
        }

        log.info("Activated group {} with {} members; rotation locked; cycle 1 period {}/{}",
            groupId, members.size(), c1PeriodStart, c1PeriodEnd);
        return toGroupResponse(group);
    }

    @Override
    @Transactional(readOnly = true)
    public List<RotationEntry> getRotation(UUID groupId) {
        SavingsGroup group = findGroupOrThrow(groupId);

        if (!group.isRotationLocked()) {
            throw AppException.badRequest("ROTATION_NOT_LOCKED",
                "Rotation has not been locked yet — activate the group first");
        }

        return memberRepository.findByGroupOrderByRotationPositionAsc(group).stream()
            .map(m -> RotationEntry.builder()
                .position(m.getRotationPosition())
                .memberId(m.getId())
                .memberName(m.getName())
                .trustScore(m.getTrustScore())
                .hasCollected(m.isHasCollected())
                .build())
            .toList();
    }

    // -------------------------------------------------------------------------
    // Ledger pool balance
    // -------------------------------------------------------------------------

    @Override
    @Transactional(readOnly = true)
    public BigDecimal getPoolBalance(UUID groupId) {
        SavingsGroup group = findGroupOrThrow(groupId);
        return computePoolBalance(group);
    }

    /**
     * Computes the group's tracked pool balance: total CREDITs minus total DEBITs recorded
     * in the ledger.  Used as the custody-free guard before any payout (Phase 2):
     * a payout DEBIT that would push balance below zero is rejected before Nomba is called.
     */
    private BigDecimal computePoolBalance(SavingsGroup group) {
        BigDecimal credits = ledgerEntryRepository
            .sumByGroupIdAndType(group.getId(), LedgerEntryType.CREDIT)
            .orElse(BigDecimal.ZERO);
        BigDecimal debits = ledgerEntryRepository
            .sumByGroupIdAndType(group.getId(), LedgerEntryType.DEBIT)
            .orElse(BigDecimal.ZERO);
        return credits.subtract(debits);
    }

    // -------------------------------------------------------------------------
    // Development helper — simulate an inbound Nomba payment_success webhook
    // -------------------------------------------------------------------------

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public Map<String, Object> simulateContribution(SimulateContributionRequest request) {
        SavingsGroup group = findGroupOrThrow(request.getGroupId());
        Member member = memberRepository.findById(request.getMemberId())
            .orElseThrow(() -> AppException.notFound("Member", request.getMemberId()));

        if (!member.getGroup().getId().equals(group.getId())) {
            throw AppException.badRequest("MEMBER_GROUP_MISMATCH",
                "Member does not belong to the specified group");
        }

        // Generate the Nomba-style requestId that drives idempotency
        String requestId = (request.getRequestId() != null && !request.getRequestId().isBlank())
            ? request.getRequestId()
            : UUID.randomUUID().toString();

        // Idempotency gate — same requestId = same event already processed
        if (webhookEventRepository.existsByProviderEventId(requestId)) {
            WebhookEvent existing = webhookEventRepository.findByProviderEventId(requestId).orElseThrow();
            return Map.of(
                "idempotent", true,
                "message", "Webhook event already processed",
                "requestId", requestId,
                "webhookEventId", existing.getId()
            );
        }

        // Build a Nomba payment_success-shaped payload for audit purposes
        String transactionId = "API-VACT_TRA-" + UUID.randomUUID().toString().substring(0, 13).toUpperCase();
        String payload = buildWebhookPayload(requestId, transactionId, request, member, group);

        // Persist the webhook event
        WebhookEvent event = new WebhookEvent();
        event.setProviderEventId(requestId);
        event.setEventType("payment_success");
        event.setRawPayload(payload);
        event.setProcessed(false);
        event.setReceivedAt(LocalDateTime.now());
        event = webhookEventRepository.save(event);

        // Ledger dedup: transactionId is the unique key
        if (ledgerEntryRepository.existsByTransactionReference(transactionId)) {
            event.setProcessed(true);
            webhookEventRepository.save(event);
            return Map.of(
                "idempotent", true,
                "message", "Ledger entry already exists for this transaction",
                "transactionId", transactionId
            );
        }

        // Compute running balance and record CREDIT
        BigDecimal currentBalance = computePoolBalance(group);
        BigDecimal newBalance = currentBalance.add(request.getAmount());

        LedgerEntry entry = new LedgerEntry();
        entry.setGroup(group);
        entry.setMember(member);
        entry.setType(LedgerEntryType.CREDIT);
        entry.setAmount(request.getAmount());
        entry.setBalanceAfter(newBalance);
        entry.setTransactionReference(transactionId);
        entry.setSource("WEBHOOK:payment_success");
        entry = ledgerEntryRepository.save(entry);

        // Also update the Contribution record so trust scoring and health endpoints are accurate
        int cycle = group.getCurrentCycle();
        Contribution contribution = contributionRepository
            .findByMemberAndGroupAndCycleNumber(member, group, cycle)
            .orElseGet(() -> {
                Contribution c = new Contribution();
                c.setMember(member);
                c.setGroup(group);
                c.setCycleNumber(cycle);
                c.setAmountExpected(group.getContributionAmount());
                c.setStatus(ContributionStatus.PENDING);
                if (group.getStartDate() != null) {
                    int dpc = daysPerCycle(group.getFrequency());
                    c.setPeriodStart(group.getStartDate().plusDays((long)(cycle - 1) * dpc));
                    c.setPeriodEnd(group.getStartDate().plusDays((long) cycle * dpc));
                }
                return c;
            });
        if (contribution.getStatus() != ContributionStatus.PAID) {
            contribution.setStatus(ContributionStatus.PAID);
            contribution.setAmountReceived(request.getAmount());
            contribution.setTransactionReference(transactionId);
            contribution.setPaidAt(LocalDateTime.now());
            contributionRepository.save(contribution);
        }

        event.setProcessed(true);
        webhookEventRepository.save(event);

        log.info("Simulated contribution: group={} member={} amount={} newBalance={}",
            group.getId(), member.getId(), request.getAmount(), newBalance);

        return Map.of(
            "idempotent", false,
            "requestId", requestId,
            "transactionId", transactionId,
            "ledgerEntryId", entry.getId(),
            "amount", request.getAmount(),
            "poolBalanceBefore", currentBalance,
            "poolBalanceAfter", newBalance,
            "simulatedPayload", payload
        );
    }

    // ── Group health (Phase 4) ─────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public GroupHealthResponse getGroupHealth(UUID groupId) {
        SavingsGroup group = findGroupOrThrow(groupId);

        List<Member> members = group.isRotationLocked()
            ? memberRepository.findByGroupOrderByRotationPositionAsc(group)
            : memberRepository.findByGroupOrderByCreatedAtAsc(group);

        // Map member id → their contribution for the current cycle
        Map<UUID, Contribution> contribByMember = contributionRepository
            .findByGroupAndCycleNumber(group, group.getCurrentCycle()).stream()
            .collect(Collectors.toMap(c -> c.getMember().getId(), c -> c));

        // Next recipient = member at rotationPosition == currentCycle who hasn't collected
        Member nextRecipient = (group.isRotationLocked() && group.getStatus() == GroupStatus.ACTIVE)
            ? memberRepository.findByGroupAndRotationPosition(group, group.getCurrentCycle())
                              .orElse(null)
            : null;

        BigDecimal poolBalance = computePoolBalance(group);

        List<GroupHealthResponse.MemberHealthEntry> entries = members.stream()
            .map(m -> {
                Contribution c = contribByMember.get(m.getId());
                return GroupHealthResponse.MemberHealthEntry.builder()
                    .memberId(m.getId())
                    .memberName(m.getName())
                    .rotationPosition(m.getRotationPosition())
                    .trustScore(m.getTrustScore())
                    .hasCollected(m.isHasCollected())
                    .currentCycleContributionStatus(c != null ? c.getStatus().name() : "NO_RECORD")
                    .currentCycleAmountReceived(c != null ? c.getAmountReceived() : null)
                    .currentCyclePeriodEnd(c != null ? c.getPeriodEnd() : null)
                    .build();
            })
            .toList();

        return GroupHealthResponse.builder()
            .groupId(group.getId())
            .groupName(group.getName())
            .status(group.getStatus().name())
            .atRisk(group.isAtRisk())
            .currentCycle(group.getCurrentCycle())
            .totalCycles(group.getTotalCycles())
            .poolBalance(poolBalance)
            .contributionAmount(group.getContributionAmount())
            .nextRecipientId(nextRecipient != null ? nextRecipient.getId() : null)
            .nextRecipientName(nextRecipient != null ? nextRecipient.getName() : null)
            .members(entries)
            .build();
    }

    // ── Upcoming dues (Phase 5) ───────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public UpcomingDueResponse getUpcomingDues(UUID groupId) {
        SavingsGroup group = findGroupOrThrow(groupId);
        LocalDate today = LocalDate.now();

        List<UpcomingDueResponse.DueEntry> entries = contributionRepository
            .findByGroupAndStatusAndPeriodEndGreaterThanEqualOrderByPeriodEndAsc(
                    group, ContributionStatus.PENDING, today)
            .stream()
            .map(c -> UpcomingDueResponse.DueEntry.builder()
                    .memberId(c.getMember().getId())
                    .memberName(c.getMember().getName())
                    .cycleNumber(c.getCycleNumber())
                    .amountExpected(c.getAmountExpected())
                    .periodStart(c.getPeriodStart())
                    .periodEnd(c.getPeriodEnd())
                    .status(c.getStatus().name())
                    .daysUntilDue(c.getPeriodEnd() != null
                            ? java.time.temporal.ChronoUnit.DAYS.between(today, c.getPeriodEnd())
                            : 0L)
                    .build())
            .toList();

        return UpcomingDueResponse.builder()
                .groupId(group.getId())
                .asOfDate(today)
                .upcomingDues(entries)
                .build();
    }

    // ── Member statement ──────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public MemberStatementResponse getMemberStatement(UUID groupId, UUID memberId) {
        SavingsGroup group = findGroupOrThrow(groupId);
        Member member = memberRepository.findById(memberId)
            .filter(m -> m.getGroup().getId().equals(group.getId()))
            .orElseThrow(() -> AppException.notFound("Member", memberId));

        List<Contribution> contributions = contributionRepository
            .findByMemberAndGroupOrderByCycleNumberAsc(member, group);

        BigDecimal totalExpected = contributions.stream()
            .map(Contribution::getAmountExpected)
            .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalPaid = contributions.stream()
            .filter(c -> c.getStatus() == ContributionStatus.PAID)
            .map(c -> c.getAmountReceived() != null ? c.getAmountReceived() : BigDecimal.ZERO)
            .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<MemberStatementResponse.ContributionEntry> entries = contributions.stream()
            .map(c -> MemberStatementResponse.ContributionEntry.builder()
                .cycleNumber(c.getCycleNumber())
                .amountExpected(c.getAmountExpected())
                .amountReceived(c.getAmountReceived())
                .status(c.getStatus().name())
                .paidAt(c.getPaidAt())
                .periodStart(c.getPeriodStart())
                .periodEnd(c.getPeriodEnd())
                .build())
            .toList();

        return MemberStatementResponse.builder()
            .groupId(group.getId())
            .memberId(member.getId())
            .memberName(member.getName())
            .rotationPosition(member.getRotationPosition())
            .hasCollected(member.isHasCollected())
            .trustScore(member.getTrustScore())
            .owedAmount(member.getOwedAmount())
            .totalExpected(totalExpected)
            .totalPaid(totalPaid)
            .contributions(entries)
            .build();
    }

    // ── Group report ──────────────────────────────────────────────────────────

    @Override
    @Transactional(readOnly = true)
    public GroupReportResponse getGroupReport(UUID groupId) {
        SavingsGroup group = findGroupOrThrow(groupId);

        List<Member> members = group.isRotationLocked()
            ? memberRepository.findByGroupOrderByRotationPositionAsc(group)
            : memberRepository.findByGroupOrderByCreatedAtAsc(group);

        Map<UUID, Contribution> contribByMember = contributionRepository
            .findByGroupAndCycleNumber(group, group.getCurrentCycle()).stream()
            .collect(Collectors.toMap(c -> c.getMember().getId(), c -> c));

        List<GroupReportResponse.MemberFundingEntry> currentCycleFunding = members.stream()
            .map(m -> {
                Contribution c = contribByMember.get(m.getId());
                return GroupReportResponse.MemberFundingEntry.builder()
                    .memberId(m.getId())
                    .memberName(m.getName())
                    .status(c != null ? c.getStatus().name() : "NO_RECORD")
                    .amount(c != null ? c.getAmountExpected() : group.getContributionAmount())
                    .build();
            })
            .toList();

        Member nextRecipient = (group.isRotationLocked() && group.getStatus() == GroupStatus.ACTIVE)
            ? memberRepository.findByGroupAndRotationPosition(group, group.getCurrentCycle())
                              .orElse(null)
            : null;

        List<PayoutResponse> payoutHistory = payoutService.getPayoutHistory(groupId);
        List<RotationEntry> rotationOrder = group.isRotationLocked() ? getRotation(groupId) : List.of();

        return GroupReportResponse.builder()
            .groupId(group.getId())
            .groupName(group.getName())
            .status(group.getStatus().name())
            .currentCycle(group.getCurrentCycle())
            .totalCycles(group.getTotalCycles())
            .poolBalance(computePoolBalance(group))
            .currentCycleFunding(currentCycleFunding)
            .payoutHistory(payoutHistory)
            .rotationOrder(rotationOrder)
            .nextToCollectMemberId(nextRecipient != null ? nextRecipient.getId() : null)
            .nextToCollectMemberName(nextRecipient != null ? nextRecipient.getName() : null)
            .build();
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    private static int daysPerCycle(Frequency frequency) {
        return switch (frequency) {
            case DAILY   -> 1;
            case WEEKLY  -> 7;
            case MONTHLY -> 30;
        };
    }

    private SavingsGroup findGroupOrThrow(UUID groupId) {
        UUID integratorId = currentIntegratorId();
        if (integratorId != null) {
            // Authenticated integrator path — scope to their own groups.
            // Return 404 (not 403) so callers can't probe for other integrators' group IDs.
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

    /**
     * Enforces the 8–64 char constraint Nomba imposes on accountName.
     * Pads with spaces to reach the minimum; truncates to stay at the maximum.
     */
    private static String toNombaAccountName(String input) {
        if (input.length() < 8) {
            return String.format("%-8s", input);
        }
        return input.length() > 64 ? input.substring(0, 64) : input;
    }

    private GroupResponse toGroupResponse(SavingsGroup group) {
        long memberCount = memberRepository.countByGroup(group);
        return GroupResponse.builder()
            .id(group.getId())
            .name(group.getName())
            .contributionAmount(group.getContributionAmount())
            .frequency(group.getFrequency().name())
            .status(group.getStatus().name())
            .currentCycle(group.getCurrentCycle())
            .totalCycles(group.getTotalCycles())
            .rotationLocked(group.isRotationLocked())
            .memberCount(memberCount)
            .callbackUrl(group.getCallbackUrl())
            .createdAt(group.getCreatedAt())
            .updatedAt(group.getUpdatedAt())
            .build();
    }

    private static MemberResponse toMemberResponse(Member member, VirtualAccount va) {
        VirtualAccountResponse vaResponse = va == null ? null : VirtualAccountResponse.builder()
            .id(va.getId())
            .accountRef(va.getAccountRef())
            .bankAccountNumber(va.getBankAccountNumber())
            .bankAccountName(va.getBankAccountName())
            .bankName(va.getBankName())
            .type(va.getType().name())
            .build();

        return MemberResponse.builder()
            .id(member.getId())
            .groupId(member.getGroup().getId())
            .name(member.getName())
            .email(member.getEmail())
            .phone(member.getPhone())
            .kycStatus(member.getKycStatus().name())
            .rotationPosition(member.getRotationPosition())
            .trustScore(member.getTrustScore())
            .hasCollected(member.isHasCollected())
            .payoutAccountNumber(member.getPayoutAccountNumber())
            .payoutBankCode(member.getPayoutBankCode())
            .payoutAccountName(member.getPayoutAccountName())
            .owedAmount(member.getOwedAmount())
            .virtualAccount(vaResponse)
            .createdAt(member.getCreatedAt())
            .build();
    }

    private static String buildWebhookPayload(
            String requestId, String transactionId,
            SimulateContributionRequest request, Member member, SavingsGroup group) {

        return """
            {
              "event_type": "payment_success",
              "requestId": "%s",
              "data": {
                "merchant": {},
                "terminal": {},
                "transaction": {
                  "transactionId": "%s",
                  "type": "vact_transfer",
                  "amount": %s,
                  "fee": 0,
                  "time": "%s",
                  "responseCode": ""
                },
                "customer": {
                  "name": "%s"
                },
                "aliasAccountReference": "%s",
                "aliasAccountName": "%s",
                "aliasGroupId": "%s"
              }
            }""".formatted(
                requestId, transactionId,
                request.getAmount().toPlainString(),
                LocalDateTime.now(),
                member.getName(),
                "MBR-" + member.getId().toString().replace("-", ""),
                member.getName(),
                group.getId()
            );
    }
}