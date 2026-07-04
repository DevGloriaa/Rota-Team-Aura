package com.aura.ajo.serviceImpl;

import com.aura.ajo.config.NombaProperties;
import com.aura.ajo.dto.NombaWebhookPayload;
import com.aura.ajo.entity.*;
import com.aura.ajo.enums.*;
import java.time.LocalDate;
import com.aura.ajo.exception.AppException;
import com.aura.ajo.repository.*;
import com.aura.ajo.service.CycleFundedCheckEvent;
import com.aura.ajo.service.WebhookService;
import org.springframework.context.ApplicationEventPublisher;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.LocalDateTime;
import java.util.Base64;

@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookServiceImpl implements WebhookService {

    private final WebhookEventRepository webhookEventRepository;
    private final VirtualAccountRepository virtualAccountRepository;
    private final ContributionRepository contributionRepository;
    private final LedgerEntryRepository ledgerEntryRepository;
    private final QuarantinedPaymentRepository quarantinedPaymentRepository;
    private final NombaProperties properties;
    private final ObjectMapper objectMapper;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    @Transactional(isolation = Isolation.REPEATABLE_READ)
    public void handleNombaWebhook(String rawPayload, String signature) {

        // ── 1. Parse payload ──────────────────────────────────────────────────
        // Parse first so we can build the Nomba signing string from the 9 documented
        // payload fields. No DB writes occur until after signature verification below.
        NombaWebhookPayload payload;
        try {
            payload = objectMapper.readValue(rawPayload, NombaWebhookPayload.class);
        } catch (JsonProcessingException e) {
            throw AppException.badRequest("INVALID_WEBHOOK_PAYLOAD",
                    "Cannot parse webhook body: " + e.getMessage());
        }

        // ── 2. Verify HMAC-SHA256 signature ──────────────────────────────────
        if (!verifySignature(payload, signature)) {
            throw new AppException("INVALID_WEBHOOK_SIGNATURE",
                    "Webhook signature verification failed — request rejected",
                    HttpStatus.UNAUTHORIZED);
        }

        String requestId = payload.getRequestId();
        if (requestId == null || requestId.isBlank()) {
            throw AppException.badRequest("MISSING_REQUEST_ID",
                    "Nomba webhook payload is missing the top-level requestId field");
        }

        // ── 3. Idempotency gate (webhook level) ───────────────────────────────
        // requestId is Nomba's designated dedup key — present on every event.
        if (webhookEventRepository.existsByProviderEventId(requestId)) {
            log.info("Duplicate webhook requestId={} — acknowledged, no processing", requestId);
            return;
        }

        // ── 4. Route by event type ────────────────────────────────────────────
        if (!"payment_success".equals(payload.getEventType())) {
            // Persist and acknowledge unrecognised events without processing.
            persistWebhookEvent(requestId, payload.getEventType(), rawPayload, true);
            log.info("Unhandled event type='{}' — persisted and acknowledged", payload.getEventType());
            return;
        }

        // ── 5. Extract transaction fields ─────────────────────────────────────
        NombaWebhookPayload.EventData data = payload.getData();
        if (data == null || data.getTransaction() == null) {
            throw AppException.badRequest("MALFORMED_WEBHOOK",
                    "payment_success event is missing data.transaction");
        }

        String transactionId = data.getTransaction().getTransactionId();
        BigDecimal amount = data.getTransaction().getAmount();
        String accountRef = data.getAliasAccountReference();

        if (transactionId == null || amount == null || accountRef == null) {
            throw AppException.badRequest("MALFORMED_WEBHOOK",
                    "payment_success event missing transactionId, amount, or aliasAccountReference");
        }

        // ── 6. Resolve member via virtual account reference ───────────────────
        // A payment that cannot be reconciled to an active PENDING contribution is quarantined
        // rather than thrown/dropped: Nomba retries on non-2xx, so we always ack (200) once the
        // event is durably recorded, whether credited or quarantined.
        VirtualAccount va = virtualAccountRepository.findByAccountRef(accountRef).orElse(null);
        if (va == null) {
            log.warn("No virtual account found for ref={} — quarantining payment", accountRef);
            quarantinePayment(null, accountRef, amount, transactionId, QuarantineReason.GROUP_NOT_FOUND);
            persistWebhookEvent(requestId, payload.getEventType(), rawPayload, true);
            return;
        }

        if (va.getType() != VirtualAccountType.MEMBER) {
            // Payments to the pool's VA reference are unexpected; log and accept.
            persistWebhookEvent(requestId, payload.getEventType(), rawPayload, true);
            log.warn("Payment arrived on pool VA ref={} — persisted but not credited to member ledger",
                    accountRef);
            return;
        }

        Member member = va.getMember();
        SavingsGroup group = va.getGroup();

        if (group.getStatus() == GroupStatus.FORMING) {
            log.warn("Payment received for group {} which has not been activated — quarantining", group.getId());
            quarantinePayment(group, va.getBankAccountNumber(), amount, transactionId, QuarantineReason.NO_ACTIVE_CYCLE);
            persistWebhookEvent(requestId, payload.getEventType(), rawPayload, true);
            return;
        }

        if (group.getStatus() == GroupStatus.COMPLETED) {
            log.warn("Payment received for group {} which has already completed — quarantining", group.getId());
            quarantinePayment(group, va.getBankAccountNumber(), amount, transactionId, QuarantineReason.CYCLE_COMPLETED);
            persistWebhookEvent(requestId, payload.getEventType(), rawPayload, true);
            return;
        }

        // ── 7. Persist webhook event (idempotency anchor) ─────────────────────
        // Saved inside this transaction; rolls back with everything else if processing fails.
        // DB UNIQUE on providerEventId prevents duplicate-insert races.
        WebhookEvent event = persistWebhookEvent(requestId, payload.getEventType(), rawPayload, false);

        // ── 8. Ledger-level dedup ─────────────────────────────────────────────
        // transactionId is Nomba's unique per-transaction ID — second guard against double-credit.
        if (ledgerEntryRepository.existsByTransactionReference(transactionId)) {
            event.setProcessed(true);
            webhookEventRepository.save(event);
            log.info("Ledger entry already exists for txId={} — skipping credit", transactionId);
            return;
        }

        // ── 9. Find or create the Contribution for this member/cycle ──────────
        int cycle = group.getCurrentCycle();
        Contribution existing = contributionRepository
                .findByMemberAndGroupAndCycleNumber(member, group, cycle)
                .orElse(null);

        boolean alreadyPaid = existing != null && existing.getStatus() == ContributionStatus.PAID;
        BigDecimal expectedAmount = existing != null ? existing.getAmountExpected() : group.getContributionAmount();

        // A second payment on top of an already-PAID contribution is allowed to differ (it's a
        // pool top-up, see the HOLD rule below) — only PENDING/MISSED contributions must match.
        if (!alreadyPaid && amount.compareTo(expectedAmount) != 0) {
            log.warn("Amount mismatch for group={} member={} cycle={}: expected={} received={} — quarantining",
                    group.getId(), member.getId(), cycle, expectedAmount, amount);
            quarantinePayment(group, va.getBankAccountNumber(), amount, transactionId, QuarantineReason.AMOUNT_MISMATCH);
            event.setProcessed(true);
            webhookEventRepository.save(event);
            return;
        }

        Contribution contribution = existing != null ? existing : newContribution(member, group, cycle);

        // ── 10. Advance Contribution status ───────────────────────────────────
        // Out-of-cycle rule (HOLD): if the contribution is already PAID, a second payment
        // for the same cycle is still credited to the pool (the money IS there) but the
        // contribution record is not updated — the extra funds carry forward to benefit the
        // next cycle's pool balance. This avoids overwriting the paidAt timestamp or the
        // original transactionReference, and prevents any trust-score distortion.
        if (contribution.getStatus() == ContributionStatus.PAID) {
            log.info("Additional payment for already-PAID contribution: member={} group={} cycle={} amount={} — crediting pool only",
                    member.getId(), group.getId(), cycle, amount);
        } else {
            // PENDING or MISSED → mark as PAID (a late payment is still better than a permanent MISSED)
            contribution.setStatus(ContributionStatus.PAID);
            contribution.setAmountReceived(amount);
            contribution.setTransactionReference(transactionId);
            contribution.setPaidAt(LocalDateTime.now());
            contributionRepository.save(contribution);
        }

        // ── 11. Write CREDIT to the ledger ────────────────────────────────────
        // balanceAfter is computed here so the ledger is self-auditing at any point in time.
        BigDecimal currentBalance = computePoolBalance(group);
        BigDecimal newBalance = currentBalance.add(amount);

        LedgerEntry entry = new LedgerEntry();
        entry.setGroup(group);
        entry.setMember(member);
        entry.setType(LedgerEntryType.CREDIT);
        entry.setAmount(amount);
        entry.setBalanceAfter(newBalance);
        entry.setTransactionReference(transactionId);
        entry.setSource("WEBHOOK:payment_success");
        ledgerEntryRepository.save(entry);

        // ── 12. Publish cycle-funded check (fires AFTER this TX commits) ─────
        // PayoutServiceImpl.onCycleFundedCheck executes the payout in a fresh TX
        // if all members have paid for this cycle. Decoupled so a payout failure
        // never rolls back the contribution record.
        eventPublisher.publishEvent(new CycleFundedCheckEvent(group.getId(), cycle));

        // ── 13. Mark webhook processed ────────────────────────────────────────
        event.setProcessed(true);
        webhookEventRepository.save(event);

        log.info("Contribution recorded: group={} member={} cycle={} amount={} poolBalance={}",
                group.getId(), member.getId(), cycle, amount, newBalance);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private WebhookEvent persistWebhookEvent(
            String requestId, String eventType, String rawPayload, boolean processed) {
        WebhookEvent event = new WebhookEvent();
        event.setProviderEventId(requestId);
        event.setEventType(eventType);
        event.setRawPayload(rawPayload);
        event.setProcessed(processed);
        event.setReceivedAt(LocalDateTime.now());
        return webhookEventRepository.save(event);
    }

    private static int daysPerCycle(Frequency frequency) {
        return switch (frequency) {
            case DAILY   -> 1;
            case WEEKLY  -> 7;
            case MONTHLY -> 30;
        };
    }

    private Contribution newContribution(Member member, SavingsGroup group, int cycle) {
        Contribution c = new Contribution();
        c.setMember(member);
        c.setGroup(group);
        c.setCycleNumber(cycle);
        c.setAmountExpected(group.getContributionAmount());
        c.setStatus(ContributionStatus.PENDING);
        // Reconstruct fixed period dates from group.startDate (edge case: payment arrives
        // before eager contribution creation, e.g. race on group activation)
        if (group.getStartDate() != null) {
            int dpc = daysPerCycle(group.getFrequency());
            c.setPeriodStart(group.getStartDate().plusDays((long) (cycle - 1) * dpc));
            c.setPeriodEnd(group.getStartDate().plusDays((long) cycle * dpc));
        }
        return c;
    }

    /**
     * Records a payment that cannot be reconciled to an active PENDING contribution.
     * groupId/integratorId are null when the virtual account reference itself is unknown.
     */
    private void quarantinePayment(SavingsGroup group, String virtualAccountNumber, BigDecimal amount,
                                    String transactionId, QuarantineReason reason) {
        QuarantinedPayment payment = new QuarantinedPayment();
        payment.setGroupId(group != null ? group.getId() : null);
        payment.setIntegratorId(group != null ? group.getIntegratorId() : null);
        payment.setVirtualAccountNumber(virtualAccountNumber);
        payment.setAmount(amount);
        payment.setNombaTransactionRef(transactionId);
        payment.setReason(reason);
        quarantinedPaymentRepository.save(payment);
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
     * Verifies the Nomba webhook signature per the Nomba signing scheme.
     *
     * Nomba computes HMAC-SHA256 over a colon-joined string of nine specific payload fields
     * (in a documented order), Base64-encodes the result, and sends it in the
     * "nomba-signature" header. See https://developer.nomba.com/docs/api-basics/webhook
     *
     * The secret is required — startup fails with @NotBlank if unset. This runtime check
     * is defense-in-depth: if somehow reached with a blank secret, it throws rather than
     * silently passing every request.
     */
    private boolean verifySignature(NombaWebhookPayload payload, String receivedSignature) {
        String secret = properties.getWebhook().getSecret();
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "NOMBA_WEBHOOK_SECRET is not configured — set the NOMBA_WEBHOOK_SECRET " +
                    "environment variable before accepting webhooks.");
        }
        String liveSecret = properties.getWebhook().getLiveSecret();

        NombaWebhookPayload.EventData data = payload.getData();
        NombaWebhookPayload.MerchantInfo merchant = data != null ? data.getMerchant() : null;
        NombaWebhookPayload.TransactionInfo tx = data != null ? data.getTransaction() : null;

        // Signing input: 9 colon-joined fields in the exact order Nomba specifies
        String signingInput = String.join(":",
                s(payload.getEventType()),
                s(payload.getRequestId()),
                s(merchant != null ? merchant.getUserId() : null),
                s(merchant != null ? merchant.getWalletId() : null),
                s(tx != null ? tx.getTransactionId() : null),
                s(tx != null ? tx.getType() : null),
                s(tx != null ? tx.getTime() : null),
                s(tx != null ? tx.getResponseCode() : null),
                s(payload.getTimestamp())
        );

        try {
            boolean matchesSecret = matchesHmac(signingInput, secret, receivedSignature);
            boolean matchesLiveSecret = liveSecret != null && !liveSecret.isBlank()
                    && matchesHmac(signingInput, liveSecret, receivedSignature);
            return matchesSecret || matchesLiveSecret;
        } catch (Exception e) {
            log.error("HMAC-SHA256 signature computation failed", e);
            return false;
        }
    }

    private static boolean matchesHmac(String signingInput, String secret, String receivedSignature)
            throws java.security.NoSuchAlgorithmException, java.security.InvalidKeyException {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        byte[] hash = mac.doFinal(signingInput.getBytes(StandardCharsets.UTF_8));
        String computed = Base64.getEncoder().encodeToString(hash);

        // Constant-time comparison prevents timing attacks
        return MessageDigest.isEqual(
                computed.getBytes(StandardCharsets.UTF_8),
                receivedSignature.getBytes(StandardCharsets.UTF_8));
    }

    private static String s(String value) {
        return value != null ? value : "";
    }
}