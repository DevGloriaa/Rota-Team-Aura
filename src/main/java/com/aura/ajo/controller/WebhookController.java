package com.aura.ajo.controller;

import com.aura.ajo.exception.ErrorResponse;
import com.aura.ajo.service.WebhookService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

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
@Tag(name = "Webhooks")
public class WebhookController {

    private final WebhookService webhookService;

    @Operation(summary = "Nomba inbound payment webhook receiver")
    @PostMapping("/nomba")
    public ResponseEntity<?> handleNomba(
            @RequestHeader(value = "nomba-signature", required = false) String signature,
            @RequestHeader(value = "nomba-timestamp", required = false) String nombaTimestamp,
            @RequestBody(required = false) String rawPayload) {

        if (rawPayload == null || rawPayload.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(ErrorResponse.builder()
                    .errorCode("INVALID_REQUEST")
                    .message("Empty payload")
                    .timestamp(LocalDateTime.now())
                    .build());
        }

        log.debug("Received Nomba webhook ({} bytes)", rawPayload.length());
        webhookService.handleNombaWebhook(rawPayload, signature != null ? signature : "",
                nombaTimestamp != null ? nombaTimestamp : "");
        return ResponseEntity.ok().build();
    }
}