package com.aura.ajo.repository;

import com.aura.ajo.entity.Member;
import com.aura.ajo.entity.SavingsGroup;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface MemberRepository extends JpaRepository<Member, UUID> {

    List<Member> findByGroupOrderByCreatedAtAsc(SavingsGroup group);

    List<Member> findByGroupOrderByRotationPositionAsc(SavingsGroup group);

    Optional<Member> findByGroupAndRotationPosition(SavingsGroup group, int rotationPosition);

    boolean existsByGroupAndEmail(SavingsGroup group, String email);

    /** Uniqueness check on rename — excludes the member being updated. */
    boolean existsByGroupAndEmailAndIdNot(SavingsGroup group, String email, UUID id);

    long countByGroup(SavingsGroup group);

    /** Cross-group completed-cycle count — used for experience bonus in trust scoring. */
    long countByEmailAndHasCollectedTrue(String email);
}