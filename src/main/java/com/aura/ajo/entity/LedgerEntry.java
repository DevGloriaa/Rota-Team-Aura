package com.aura.ajo.entity;

import com.aura.ajo.enums.LedgerEntryType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "ledger_entries")
@Getter
@Setter
@NoArgsConstructor
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private SavingsGroup group;

    /** Nullable — some entries are group-level (e.g., payout debit from pool). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LedgerEntryType type;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    /**
     * Running pool balance after this entry is applied.
     * Computed at write time; enables point-in-time balance lookup without summing all rows.
     */
    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    /**
     * Dedup key. For contribution credits: data.transaction.transactionId from Nomba webhook.
     * For payout debits: merchantTxRef ("PAYOUT-{groupId}-CYC-{n}").
     * UNIQUE constraint makes double-credit/double-debit impossible even under retries.
     */
    @Column(nullable = false, unique = true)
    private String transactionReference;

    /** Human-readable origin: "CONTRIBUTION", "PAYOUT:CYC-3", "WEBHOOK:payment_success", etc. */
    @Column(nullable = false)
    private String source;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}