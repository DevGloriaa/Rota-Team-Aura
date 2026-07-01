package com.aura.ajo.repository;

import com.aura.ajo.entity.Integrator;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface IntegratorRepository extends JpaRepository<Integrator, UUID> {

    Optional<Integrator> findByApiKeyHash(String apiKeyHash);
}
