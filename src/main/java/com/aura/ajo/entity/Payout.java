package com.aura.ajo.entity;

import com.aura.ajo.enums.PayoutStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "payouts",
    uniqueConstraints = @UniqueConstraint(
        name = "ux_payout_group_cycle",
        columnNames = {"group_id", "cycle_number"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class Payout {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private SavingsGroup group;

    /**
     * Recipient is always the next member in the locked rotation — resolved by the service,
     * never supplied by the caller. This makes an arbitrary payout destination architecturally
     * impossible, not just validated.
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "recipient_member_id", nullable = false)
    private Member recipientMember;

    @Column(name = "cycle_number", nullable = false)
    private int cycleNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PayoutStatus status = PayoutStatus.PENDING;

    /** Transaction ID returned by Nomba after a successful bank transfer. */
    private String nombaTransactionId;

    /**
     * Idempotency key sent to Nomba: "PAYOUT-{groupId}-CYC-{cycleNumber}".
     * Deterministic construction means even if we retry, Nomba rejects the duplicate
     * and our UNIQUE constraint catches it before we even call Nomba.
     */
    @Column(nullable = false, unique = true)
    private String merchantTxRef;

    private LocalDateTime executedAt;

    /** Populated only on FAILED payouts; null for COMPLETED. */
    private String failureReason;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}