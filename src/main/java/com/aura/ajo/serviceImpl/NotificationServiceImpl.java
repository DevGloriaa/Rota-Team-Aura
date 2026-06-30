package com.aura.ajo.serviceImpl;

import com.aura.ajo.entity.Member;
import com.aura.ajo.entity.OutboundNotification;
import com.aura.ajo.entity.SavingsGroup;
import com.aura.ajo.enums.NotificationEventType;
import com.aura.ajo.repository.OutboundNotificationRepository;
import com.aura.ajo.service.NotificationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.math.BigDecimal;
import java.time.LocalDate;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationServiceImpl implements NotificationService {

    private final OutboundNotificationRepository notificationRepository;

    private final RestClient callbackClient = RestClient.builder().build();

    // ── Interface methods ─────────────────────────────────────────────────────

    @Override
    public void fireContributionDueSoon(
            SavingsGroup group, int cycleNumber, Member member, LocalDate periodEnd) {
        if (notificationRepository.existsByGroupAndCycleNumberAndEventTypeAndMember(
                group, cycleNumber, NotificationEventType.CONTRIBUTION_DUE_SOON, member)) {
            return;
        }
        String payload = buildMemberPayload(NotificationEventType.CONTRIBUTION_DUE_SOON,
                group, cycleNumber, member, periodEnd, group.getContributionAmount());
        deliver(save(group, member, NotificationEventType.CONTRIBUTION_DUE_SOON, cycleNumber, payload),
                group.getCallbackUrl());
    }

    @Override
    public void fireContributionDueNow(
            SavingsGroup group, int cycleNumber, Member member, LocalDate periodEnd) {
        if (notificationRepository.existsByGroupAndCycleNumberAndEventTypeAndMember(
                group, cycleNumber, NotificationEventType.CONTRIBUTION_DUE_NOW, member)) {
            return;
        }
        String payload = buildMemberPayload(NotificationEventType.CONTRIBUTION_DUE_NOW,
                group, cycleNumber, member, periodEnd, group.getContributionAmount());
        deliver(save(group, member, NotificationEventType.CONTRIBUTION_DUE_NOW, cycleNumber, payload),
                group.getCallbackUrl());
    }

    @Override
    public void fireContributionMissed(
            SavingsGroup group, int cycleNumber, Member member, BigDecimal amountMissed) {
        if (notificationRepository.existsByGroupAndCycleNumberAndEventTypeAndMember(
                group, cycleNumber, NotificationEventType.CONTRIBUTION_MISSED, member)) {
            return;
        }
        String payload = """
                {"event":"CONTRIBUTION_MISSED","groupId":"%s","groupName":"%s",\
                "cycleNumber":%d,"memberId":"%s","memberName":"%s","amountMissed":%s}"""
                .formatted(group.getId(), esc(group.getName()), cycleNumber,
                        member.getId(), esc(member.getName()), amountMissed.toPlainString());
        deliver(save(group, member, NotificationEventType.CONTRIBUTION_MISSED, cycleNumber, payload),
                group.getCallbackUrl());
    }

    @Override
    public void fireCycleFunded(SavingsGroup group, int cycleNumber, BigDecimal payoutAmount) {
        if (notificationRepository.existsByGroupAndCycleNumberAndEventTypeAndMemberIsNull(
                group, cycleNumber, NotificationEventType.CYCLE_FUNDED)) {
            return;
        }
        String payload = """
                {"event":"CYCLE_FUNDED","groupId":"%s","groupName":"%s",\
                "cycleNumber":%d,"payoutAmount":%s}"""
                .formatted(group.getId(), esc(group.getName()), cycleNumber,
                        payoutAmount.toPlainString());
        deliver(save(group, null, NotificationEventType.CYCLE_FUNDED, cycleNumber, payload),
                group.getCallbackUrl());
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private OutboundNotification save(SavingsGroup group, Member member,
            NotificationEventType eventType, int cycleNumber, String payload) {
        OutboundNotification n = new OutboundNotification();
        n.setGroup(group);
        n.setMember(member);
        n.setEventType(eventType);
        n.setCycleNumber(cycleNumber);
        n.setPayload(payload);
        n.setDeliveredToCallback(false);
        return notificationRepository.save(n);
    }

    private void deliver(OutboundNotification notification, String callbackUrl) {
        if (callbackUrl == null || callbackUrl.isBlank()) {
            return;
        }
        try {
            callbackClient.post()
                    .uri(callbackUrl)
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(notification.getPayload())
                    .retrieve()
                    .toBodilessEntity();
            notification.setDeliveredToCallback(true);
            notificationRepository.save(notification);
            log.info("Notification {} delivered to {}", notification.getEventType(), callbackUrl);
        } catch (Exception e) {
            log.warn("Notification {} delivery to {} failed: {}",
                    notification.getEventType(), callbackUrl, e.getMessage());
        }
    }

    private String buildMemberPayload(NotificationEventType eventType, SavingsGroup group,
            int cycleNumber, Member member, LocalDate periodEnd, BigDecimal amountExpected) {
        return """
                {"event":"%s","groupId":"%s","groupName":"%s","cycleNumber":%d,\
                "memberId":"%s","memberName":"%s","amountExpected":%s,"periodEnd":"%s"}"""
                .formatted(eventType.name(), group.getId(), esc(group.getName()), cycleNumber,
                        member.getId(), esc(member.getName()),
                        amountExpected.toPlainString(), periodEnd);
    }

    /** Minimal JSON string escaping — only escapes double-quotes and backslashes. */
    private static String esc(String s) {
        return s == null ? "" : s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}