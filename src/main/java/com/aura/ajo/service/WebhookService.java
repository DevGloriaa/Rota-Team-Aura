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
     * @param rawPayload   the raw request body string (needed for HMAC verification)
     * @param signature    value of the x-nomba-signature header; empty string if header absent
     */
    void handleNombaWebhook(String rawPayload, String signature);
}