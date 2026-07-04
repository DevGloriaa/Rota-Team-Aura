package com.aura.ajo.config;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;
import org.springframework.validation.annotation.Validated;

@Component
@ConfigurationProperties(prefix = "nomba")
@Getter
@Setter
@Validated
public class NombaProperties {

    private Api api = new Api();

    @Valid
    private Webhook webhook = new Webhook();

    @Getter
    @Setter
    public static class Api {
        private String baseUrl;
        private String accountId;
        private String subAccountId;
        private String clientId;
        private String clientSecret;
    }

    @Getter
    @Setter
    public static class Webhook {
        /**
         * HMAC-SHA256 signing secret from the Nomba dashboard.
         * Set via NOMBA_WEBHOOK_SECRET env var. Required — startup fails if blank.
         */
        @NotBlank(message = "nomba.webhook.secret must be set (env: NOMBA_WEBHOOK_SECRET) — " +
                            "signature verification requires a configured secret key")
        private String secret;

        /**
         * Live-environment HMAC signing secret from the Nomba dashboard.
         * Set via NOMBA_LIVE_WEBHOOK_SECRET env var. Optional — when set, webhooks are
         * accepted if they match either this or {@link #secret}, so sandbox and live
         * webhooks can both be verified during the cutover period.
         */
        private String liveSecret;
    }
}