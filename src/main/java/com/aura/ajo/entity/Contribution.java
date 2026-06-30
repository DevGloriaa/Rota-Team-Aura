package com.aura.ajo.entity;

import com.aura.ajo.enums.ContributionStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "contributions")
@Getter
@Setter
@NoArgsConstructor
public class Contribution {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id", nullable = false)
    private Member member;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private SavingsGroup group;

    @Column(nullable = false)
    private int cycleNumber;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amountExpected;

    @Column(precision = 19, scale = 4)
    private BigDecimal amountReceived;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ContributionStatus status = ContributionStatus.PENDING;

    /** Fixed window start for this cycle, anchored to group.startDate. Never shifts after activation. */
    private LocalDate periodStart;

    /** Fixed window end for this cycle. PENDING → MISSED when periodEnd passes unpaid. */
    private LocalDate periodEnd;

    /**
     * Timestamp when this contribution was recorded as PAID.
     * Compared against periodEnd to determine on-time vs late — drives trust scoring.
     * Null for PENDING or MISSED contributions.
     */
    private LocalDateTime paidAt;

    @Column(unique = true)
    private String transactionReference;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}