package com.aura.ajo.service;

/**
 * Processes inbound Nomba webhook events.
 *
 * Contract:
 *   - Verifies the HMAC-SHA256 signature from the x-nomba-signature header.
 *   - Is fully idempotent: calling with the same requestId twice is safe and produces a 200
 *     on the second call with no side effects.
 *   - All database writes (WebhookEvent, Contribution, LedgerEntry) happen in one transaction.
 */
public interface WebhookService {

    /**
     * @param rawPayload      the raw request body string (needed for HMAC verification)
     * @param signature       value of the nomba-signature header; empty string if header absent
     * @param nombaTimestamp  value of the nomba-timestamp header; empty string if header absent.
     *                        Used as the 9th field of the HMAC signing input per Nomba's docs —
     *                        NOT the timestamp field inside the JSON payload body.
     */
    void handleNombaWebhook(String rawPayload, String signature, String nombaTimestamp);
}