package com.aura.ajo.entity;

import com.aura.ajo.enums.NotificationEventType;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "outbound_notifications")
@Getter
@Setter
@NoArgsConstructor
public class OutboundNotification {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = false)
    private SavingsGroup group;

    /** Null for group-level events (e.g. CYCLE_FUNDED). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "member_id")
    private Member member;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationEventType eventType;

    @Column(nullable = false)
    private int cycleNumber;

    /** JSON-serialised event details for client consumption. */
    @Column(columnDefinition = "TEXT", nullable = false)
    private String payload;

    /**
     * True when the group's registered callbackUrl received a successful 2xx response.
     * False means delivery failed or no callbackUrl was registered — the record in this
     * table is the durable source of truth regardless of delivery status.
     */
    @Column(nullable = false)
    private boolean deliveredToCallback = false;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}