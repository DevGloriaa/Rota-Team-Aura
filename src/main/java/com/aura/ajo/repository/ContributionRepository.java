package com.aura.ajo.repository;

import com.aura.ajo.entity.Contribution;
import com.aura.ajo.entity.Member;
import com.aura.ajo.entity.SavingsGroup;
import com.aura.ajo.enums.ContributionStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ContributionRepository extends JpaRepository<Contribution, UUID> {

    List<Contribution> findByGroupAndCycleNumber(SavingsGroup group, int cycleNumber);

    Optional<Contribution> findByTransactionReference(String transactionReference);

    Optional<Contribution> findByMemberAndGroupAndCycleNumber(
            Member member, SavingsGroup group, int cycleNumber);

    long countByGroupAndCycleNumberAndStatus(
            SavingsGroup group, int cycleNumber, ContributionStatus status);

    /** All contributions for a member's email across every group — drives portable trust scoring. */
    List<Contribution> findByMemberEmail(String email);

    /** Used by the default-detection job to find contributions whose period has expired. */
    List<Contribution> findByStatusAndPeriodEndBefore(ContributionStatus status, LocalDate date);

    /** Used by the detection job to find contributions due on an exact date (for notifications). */
    List<Contribution> findByStatusAndPeriodEnd(ContributionStatus status, LocalDate date);

    /** Returns all non-PAID contributions for a group, ordered by period end ascending. */
    List<Contribution> findByGroupAndStatusNotAndPeriodEndGreaterThanEqualOrderByPeriodEndAsc(
            SavingsGroup group, ContributionStatus excludedStatus, LocalDate fromDate);
}