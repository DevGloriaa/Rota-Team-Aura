package com.aura.ajo.entity;

import com.aura.ajo.enums.KycStatus;
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
    name = "members",
    uniqueConstraints = @UniqueConstraint(
        name = "ux_member_group_email",
        columnNames = {"group_id", "email"}
    )
)
@Getter
@Setter
@NoArgsConstructor
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private SavingsGroup group;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String email;

    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private KycStatus kycStatus = KycStatus.PENDING;

    private Integer rotationPosition;

    @Column(nullable = false)
    private int trustScore = 0;

    @Column(nullable = false)
    private boolean hasCollected = false;

    private String nombaAccountId;

    /** Nigerian bank account number — verified at registration, immutable once group is ACTIVE. */
    @Column(updatable = false)
    private String payoutAccountNumber;

    /** CBN bank code (e.g. "058") — verified at registration, immutable once group is ACTIVE. */
    @Column(updatable = false)
    private String payoutBankCode;

    /** Account name confirmed by Nomba's bank-resolve call at registration time. */
    private String payoutAccountName;

    /**
     * Cumulative amount this member owes from MISSED contributions across all cycles.
     * Incremented by amountExpected each time a contribution is marked MISSED.
     * Not automatically cleared — represents outstanding debt to the group.
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal owedAmount = BigDecimal.ZERO;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}