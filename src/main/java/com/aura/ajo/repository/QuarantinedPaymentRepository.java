package com.aura.ajo.repository;

import com.aura.ajo.entity.QuarantinedPayment;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface QuarantinedPaymentRepository extends JpaRepository<QuarantinedPayment, UUID> {

    List<QuarantinedPayment> findByGroupIdOrderByCreatedAtDesc(UUID groupId);
}
