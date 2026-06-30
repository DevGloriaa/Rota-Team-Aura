package com.aura.ajo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Body for POST /v2/transfers/wallet.
 * merchantTxRef is Nomba's own idempotency key — must be unique per transaction.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NombaWalletTransferRequest {

    private BigDecimal amount;

    /** UUID of the receiving Nomba sub-account or wallet. */
    private String receiverAccountId;

    /**
     * Idempotency key. Convention: "PAYOUT-{groupId}-CYC-{cycleNumber}".
     * Nomba rejects duplicate merchantTxRef; our DB UNIQUE constraint catches it first.
     */
    private String merchantTxRef;

    private String narration;
}