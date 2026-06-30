package com.aura.ajo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NombaBankResolveResponse {

    private String code;
    private String description;
    private ResolveData data;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class ResolveData {
        private String accountNumber;
        private String accountName;
        private String bankCode;
        private String bankName;
    }
}