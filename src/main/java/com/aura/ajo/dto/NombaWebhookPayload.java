package com.aura.ajo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Inbound webhook payload shape from Nomba.
 * Field names match the real Nomba webhook API exactly (snake_case mapped via @JsonProperty).
 * @JsonIgnoreProperties(ignoreUnknown = true) absorbs any new fields Nomba adds without breaking.
 *
 * Key idempotency fields:
 *   requestId                        → WebhookEvent.providerEventId (dedup at webhook level)
 *   data.transaction.transactionId   → LedgerEntry.transactionReference (dedup at ledger level)
 *
 * Key routing field:
 *   data.transaction.aliasAccountReference → VirtualAccount.accountRef (member lookup)
 */
@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NombaWebhookPayload {

    @JsonProperty("event_type")
    private String eventType;

    /** Top-level UUID Nomba sends on every event. Used as the webhook-level idempotency key. */
    private String requestId;

    /** Timestamp field included in the Nomba signing string (9th field). */
    private String timestamp;

    private EventData data;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class EventData {
        private MerchantInfo merchant;
        private TransactionInfo transaction;
        private CustomerInfo customer;

        private String aliasGroupId;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class MerchantInfo {
        private String walletId;
        private String walletBalance;
        private String userId;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class TransactionInfo {
        /** e.g. "API-VACT_TRA-B7B10-...". Used as LedgerEntry.transactionReference dedup key. */
        private String transactionId;
        private String type;
        private BigDecimal transactionAmount;
        private BigDecimal fee;
        private String time;
        private String responseCode;

        /** accountRef we registered with Nomba at VA creation — our correlation key to Member. */
        private String aliasAccountReference;
        private String aliasAccountNumber;
        private String aliasAccountName;
    }

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class CustomerInfo {
        private String bankCode;
        private String name;
        private String accountNumber;
        private String bankName;
    }
}