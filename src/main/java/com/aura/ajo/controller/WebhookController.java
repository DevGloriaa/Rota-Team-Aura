package com.aura.ajo.controller;

import com.aura.ajo.service.WebhookService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Receives inbound webhooks from Nomba.
 *
 * The body is consumed as a raw String so the HMAC-SHA256 signature
 * can be verified against the exact bytes Nomba signed.
 *
 * Nomba retries on non-2xx responses, so we always return 200 once the
 * event is accepted (idempotency handled inside WebhookService).
 * Authenticity failures return 401 so Nomba does NOT retry bad requests.
 *
 * curl (manual test — see README for how to compute the signature):
 *   curl -s -X POST http://localhost:8080/webhooks/nomba \
 *     -H "Content-Type: application/json" \
 *     -H "x-nomba-signature: <hmac-sha256-hex>" \
 *     -d '{"event_type":"payment_success","requestId":"<uuid>","data":{...}}'
 */
@RestController
@RequestMapping("/webhooks")
@RequiredArgsConstructor
@Slf4j
public class WebhookController {

    private final WebhookService webhookService;

    @PostMapping("/nomba")
    public ResponseEntity<Void> handleNomba(
            @RequestHeader(value = "nomba-signature", required = false) String signature,
            @RequestBody String rawPayload) {

        log.debug("Received Nomba webhook ({} bytes)", rawPayload.length());
        webhookService.handleNombaWebhook(rawPayload, signature != null ? signature : "");
        return ResponseEntity.ok().build();
    }
}