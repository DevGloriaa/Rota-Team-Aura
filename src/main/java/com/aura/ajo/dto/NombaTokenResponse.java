package com.aura.ajo.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class NombaTokenResponse {

    private String code;
    private String description;
    private TokenData data;

    @Data
    @NoArgsConstructor
    public static class TokenData {
        private String businessId;

        @JsonProperty("access_token")
        private String accessToken;

        @JsonProperty("refresh_token")
        private String refreshToken;

        /** ISO 8601 expiry timestamp, e.g. "2025-01-15T10:30:00Z". */
        private String expiresAt;
    }
}