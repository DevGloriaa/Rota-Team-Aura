package com.aura.ajo.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "integrators")
@Getter
@Setter
@NoArgsConstructor
public class Integrator {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private String name;

    /** SHA-256 hex digest of the raw API key. Raw key is never stored. */
    @Column(nullable = false, unique = true)
    private String apiKeyHash;

    private String webhookUrl;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;
}
