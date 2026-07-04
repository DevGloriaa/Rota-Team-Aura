package com.aura.ajo.entity;

import com.aura.ajo.enums.QuarantineReason;
import com.aura.ajo.enums.QuarantineStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "quarantined_payments")
@Getter
@Setter
@NoArgsConstructor
public class QuarantinedPayment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Null when the payment could not be traced to any group (e.g. GROUP_NOT_FOUND). */
    private UUID groupId;

    /** Mirrored from the group at quarantine time; null when the group is unknown. */
    private UUID integratorId;

    @Column(nullable = false)
    private String virtualAccountNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private String nombaTransactionRef;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuarantineReason reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private QuarantineStatus status = QuarantineStatus.QUARANTINED;

    private String resolutionNote;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime resolvedAt;
}
