package com.aura.ajo.repository;

import com.aura.ajo.entity.LedgerEntry;
import com.aura.ajo.enums.LedgerEntryType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    boolean existsByTransactionReference(String transactionReference);

    Optional<LedgerEntry> findByTransactionReference(String transactionReference);


    @Query("SELECT SUM(e.amount) FROM LedgerEntry e WHERE e.group.id = :groupId AND e.type = :type")
    Optional<BigDecimal> sumByGroupIdAndType(
        @Param("groupId") UUID groupId,
        @Param("type") LedgerEntryType type
    );
}