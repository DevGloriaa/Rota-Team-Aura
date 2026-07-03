package com.aura.ajo.repository;

import com.aura.ajo.enums.GroupStatus;
import com.aura.ajo.entity.SavingsGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SavingsGroupRepository extends JpaRepository<SavingsGroup, UUID> {

    Optional<SavingsGroup> findByIdAndStatus(UUID id, GroupStatus status);

    Optional<SavingsGroup> findByIdAndIntegratorId(UUID id, UUID integratorId);

    List<SavingsGroup> findByIntegratorIdOrderByCreatedAtDesc(UUID integratorId);
}