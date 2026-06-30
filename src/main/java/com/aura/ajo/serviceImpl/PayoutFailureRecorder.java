package com.aura.ajo.serviceImpl;

import com.aura.ajo.entity.Member;
import com.aura.ajo.entity.Payout;
import com.aura.ajo.entity.SavingsGroup;
import com.aura.ajo.enums.PayoutStatus;
import com.aura.ajo.repository.MemberRepository;
import com.aura.ajo.repository.PayoutRepository;
import com.aura.ajo.repository.SavingsGroupRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Persists FAILED payout records in an independent transaction (REQUIRES_NEW) so the
 * failure survives any rollback in the calling transaction. Safe to call from catch blocks.
 * Package-private — used only by PayoutServiceImpl.
 */
@Component
@RequiredArgsConstructor
@Slf4j
class PayoutFailureRecorder {

    private final PayoutRepository payoutRepository;
    private final SavingsGroupRepository groupRepository;
    private final MemberRepository memberRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    void record(UUID groupId, int cycleNumber, String reason) {
        SavingsGroup group = groupRepository.findById(groupId).orElse(null);
        if (group == null) {
            log.error("Cannot record payout failure — group {} not found", groupId);
            return;
        }

        String merchantTxRef = "PAYOUT-" + groupId + "-CYC-" + cycleNumber;
        if (payoutRepository.findByMerchantTxRef(merchantTxRef).isPresent()) {
            return; // a record (COMPLETED or previously FAILED) already exists
        }

        Member recipient = memberRepository
                .findByGroupAndRotationPosition(group, cycleNumber)
                .orElse(null);
        if (recipient == null) {
            log.error("Cannot record FAILED payout for group={} cycle={} — no member at rotation position {}",
                    groupId, cycleNumber, cycleNumber);
            return;
        }

        Payout failed = new Payout();
        failed.setGroup(group);
        failed.setRecipientMember(recipient);
        failed.setCycleNumber(cycleNumber);
        failed.setAmount(BigDecimal.ZERO);
        failed.setStatus(PayoutStatus.FAILED);
        failed.setMerchantTxRef(merchantTxRef);
        failed.setFailureReason(reason);
        payoutRepository.save(failed);

        log.warn("Recorded FAILED payout for group={} cycle={}: {}", groupId, cycleNumber, reason);
    }
}
