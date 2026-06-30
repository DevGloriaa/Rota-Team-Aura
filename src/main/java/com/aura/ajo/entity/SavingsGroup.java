package com.aura.ajo.entity;

import com.aura.ajo.enums.Frequency;
import com.aura.ajo.enums.GroupStatus;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "savings_groups")
@Getter
@Setter
@NoArgsConstructor
public class SavingsGroup {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false, precision = 19, scale = 4)
    private BigDecimal contributionAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Frequency frequency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private GroupStatus status = GroupStatus.FORMING;

    @Column(nullable = false)
    private int currentCycle = 0;

    /** Set equal to member count when the group activates; drives cycle completion logic. */
    @Column(nullable = false)
    private int totalCycles = 0;

    @Column(nullable = false)
    private boolean rotationLocked = false;

    /** Set to LocalDate.now() when the group activates. Anchors the fixed cycle schedule. */
    private LocalDate startDate;

    /**
     * Optional URL the group creator registered to receive outbound webhook notifications
     * (contribution-due-soon, due-now, missed, cycle-funded). Null means no push delivery —
     * events are still stored in outbound_notifications for polling.
     */
    private String callbackUrl;

    /**
     * Set true when any member's contribution in this group reaches MISSED status.
     * Signals the group is at risk of not completing its cycle and surfaces on the dashboard.
     * Never cleared automatically — requires manual review.
     */
    @Column(nullable = false)
    private boolean atRisk = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(nullable = false)
    private LocalDateTime updatedAt;
}