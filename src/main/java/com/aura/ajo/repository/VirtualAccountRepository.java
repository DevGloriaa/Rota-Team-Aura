package com.aura.ajo.repository;

import com.aura.ajo.entity.Member;
import com.aura.ajo.entity.SavingsGroup;
import com.aura.ajo.entity.VirtualAccount;
import com.aura.ajo.enums.VirtualAccountType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface VirtualAccountRepository extends JpaRepository<VirtualAccount, UUID> {

    Optional<VirtualAccount> findByMemberAndType(Member member, VirtualAccountType type);

    Optional<VirtualAccount> findByGroupAndTypeAndMemberIsNull(SavingsGroup group, VirtualAccountType type);

    boolean existsByMemberAndType(Member member, VirtualAccountType type);

    boolean existsByGroupAndTypeAndMemberIsNull(SavingsGroup group, VirtualAccountType type);

    /** Lookup by the accountRef we registered with Nomba — used to route inbound webhooks. */
    Optional<VirtualAccount> findByAccountRef(String accountRef);
}