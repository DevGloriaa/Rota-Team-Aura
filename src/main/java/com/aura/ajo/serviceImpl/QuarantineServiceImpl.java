package com.aura.ajo.serviceImpl;

import com.aura.ajo.dto.QuarantinedPaymentResponse;
import com.aura.ajo.dto.ResolveQuarantineRequest;
import com.aura.ajo.entity.Integrator;
import com.aura.ajo.entity.QuarantinedPayment;
import com.aura.ajo.entity.SavingsGroup;
import com.aura.ajo.enums.QuarantineStatus;
import com.aura.ajo.exception.AppException;
import com.aura.ajo.repository.QuarantinedPaymentRepository;
import com.aura.ajo.repository.SavingsGroupRepository;
import com.aura.ajo.service.QuarantineService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Slf4j
public class QuarantineServiceImpl implements QuarantineService {

    private final QuarantinedPaymentRepository quarantinedPaymentRepository;
    private final SavingsGroupRepository groupRepository;

    @Override
    @Transactional(readOnly = true)
    public List<QuarantinedPaymentResponse> getQuarantinedPayments(UUID groupId) {
        SavingsGroup group = findGroupOrThrow(groupId);
        return quarantinedPaymentRepository.findByGroupIdOrderByCreatedAtDesc(group.getId())
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public QuarantinedPaymentResponse resolve(UUID quarantineId, ResolveQuarantineRequest request) {
        QuarantinedPayment payment = quarantinedPaymentRepository.findById(quarantineId)
                .orElseThrow(() -> AppException.notFound("QuarantinedPayment", quarantineId));

        UUID integratorId = currentIntegratorId();
        if (integratorId != null && payment.getIntegratorId() != null
                && !payment.getIntegratorId().equals(integratorId)) {
            // Return 404 (not 403) so callers can't probe for other integrators' records.
            throw AppException.notFound("QuarantinedPayment", quarantineId);
        }

        if (payment.getStatus() == QuarantineStatus.RESOLVED) {
            throw AppException.conflict("ALREADY_RESOLVED",
                    "Quarantined payment " + quarantineId + " is already resolved");
        }

        payment.setStatus(QuarantineStatus.RESOLVED);
        payment.setResolutionNote(request.getResolutionNote());
        payment.setResolvedAt(LocalDateTime.now());
        quarantinedPaymentRepository.save(payment);

        log.info("Quarantined payment {} resolved: {}", quarantineId, request.getResolutionNote());
        return toResponse(payment);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    private SavingsGroup findGroupOrThrow(UUID groupId) {
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

    private QuarantinedPaymentResponse toResponse(QuarantinedPayment payment) {
        return QuarantinedPaymentResponse.builder()
                .id(payment.getId())
                .groupId(payment.getGroupId())
                .virtualAccountNumber(payment.getVirtualAccountNumber())
                .amount(payment.getAmount())
                .nombaTransactionRef(payment.getNombaTransactionRef())
                .reason(payment.getReason().name())
                .status(payment.getStatus().name())
                .resolutionNote(payment.getResolutionNote())
                .createdAt(payment.getCreatedAt())
                .resolvedAt(payment.getResolvedAt())
                .build();
    }
}
