package com.aura.ajo.repository;

import com.aura.ajo.entity.Member;
import com.aura.ajo.entity.OutboundNotification;
import com.aura.ajo.entity.SavingsGroup;
import com.aura.ajo.enums.NotificationEventType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface OutboundNotificationRepository extends JpaRepository<OutboundNotification, UUID> {

    /** Member-scoped idempotency check (DUE_SOON, DUE_NOW, MISSED). */
    boolean existsByGroupAndCycleNumberAndEventTypeAndMember(
            SavingsGroup group, int cycleNumber, NotificationEventType eventType, Member member);

    /** Group-scoped idempotency check for events without a specific member (CYCLE_FUNDED). */
    boolean existsByGroupAndCycleNumberAndEventTypeAndMemberIsNull(
            SavingsGroup group, int cycleNumber, NotificationEventType eventType);

    List<OutboundNotification> findByGroupOrderByCreatedAtDesc(SavingsGroup group);
}