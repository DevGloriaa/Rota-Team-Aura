package com.aura.ajo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "webhook_events")
@Getter
@Setter
@NoArgsConstructor
public class WebhookEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Nomba's requestId field — the top-level UUID present on every webhook payload.
     * Nomba explicitly designates it as the idempotency key for safe re-delivery handling.
     * UNIQUE constraint here is the last line of defence against double-processing.
     */
    @Column(nullable = false, unique = true)
    private String providerEventId;

    @Column(nullable = false)
    private String eventType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String rawPayload;

    @Column(nullable = false)
    private boolean processed = false;

    @Column(nullable = false)
    private LocalDateTime receivedAt;
}