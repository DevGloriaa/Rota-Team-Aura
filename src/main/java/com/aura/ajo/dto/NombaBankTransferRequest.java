package com.aura.ajo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NombaBankTransferRequest {

    private BigDecimal amount;
    /** Nigerian bank account number of the payout recipient. */
    private String receiverAccountNumber;
    /** CBN bank code (e.g. "058" for GTBank). */
    private String receiverBankCode;
    private String narration;
    /** Deterministic idempotency key sent to Nomba: "PAYOUT-{groupId}-CYC-{n}". */
    private String merchantTxRef;
}