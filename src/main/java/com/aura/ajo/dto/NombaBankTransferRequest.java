package com.aura.ajo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NombaBankTransferRequest {

    /** Nomba's live /v2/transfers/bank endpoint requires amount as a String, not a number. */
    private String amount;
    /** Nigerian bank account number of the payout recipient. */
    private String accountNumber;
    /** Name on the recipient's bank account. */
    private String accountName;
    /** CBN bank code (e.g. "058" for GTBank). */
    private String bankCode;
    /** Name of the party sending the transfer. */
    private String senderName;
    private String narration;
    /** Deterministic idempotency key sent to Nomba: "PAYOUT-{groupId}-CYC-{n}". */
    private String merchantTxRef;
}