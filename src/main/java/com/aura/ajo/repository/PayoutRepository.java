package com.aura.ajo.repository;

import com.aura.ajo.entity.Payout;
import com.aura.ajo.entity.SavingsGroup;
import com.aura.ajo.enums.PayoutStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayoutRepository extends JpaRepository<Payout, UUID> {

    boolean existsByGroupAndCycleNumber(SavingsGroup group, int cycleNumber);

    boolean existsByGroupAndCycleNumberAndStatusNot(SavingsGroup group, int cycleNumber, PayoutStatus status);

    Optional<Payout> findByMerchantTxRef(String merchantTxRef);

    List<Payout> findByGroupOrderByCycleNumberAsc(SavingsGroup group);
}