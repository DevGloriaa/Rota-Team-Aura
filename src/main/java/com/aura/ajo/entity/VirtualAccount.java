package com.aura.ajo.entity;

import com.aura.ajo.enums.VirtualAccountType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "virtual_accounts")
@Getter
@Setter
@NoArgsConstructor
public class VirtualAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private SavingsGroup group;

    /** Null for GROUP_POOL type — pool is a logical ledger balance, not a real member account. */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    /** The reference we passed to Nomba at creation time; our system-unique correlation key. */
    @Column(nullable = false, unique = true)
    private String accountRef;

    @Column(nullable = false)
    private String bankAccountNumber;

    @Column(nullable = false)
    private String bankAccountName;

    @Column(nullable = false)
    private String bankName;

    /** accountHolderId from the Nomba create-virtual-account response. */
    private String nombaAccountHolderId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private VirtualAccountType type;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}