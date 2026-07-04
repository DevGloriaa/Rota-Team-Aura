package com.aura.ajo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NombaBankTransferResponse {

    private String code;
    private String description;
    private boolean status;
    private BankTransferData data;

    @Data
    @NoArgsConstructor
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class BankTransferData {
        private String id;
        private BigDecimal amount;
        private BigDecimal fee;
        private String type;
        private String status;
        private String timeCreated;
        private BankTransferMeta meta;

        @Data
        @NoArgsConstructor
        @JsonIgnoreProperties(ignoreUnknown = true)
        public static class BankTransferMeta {
            private String merchantTxRef;
            private String rrn;
        }
    }
}