package com.aura.ajo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Response from POST /v2/transfers/wallet.
 * Field names match the real Nomba API response exactly.
 * Inner classes named TransferData/TransferMeta (not Data/Meta) to avoid
 * the Java shadowing rule with `import lombok.Data`.
 */
@Data
@NoArgsConstructor
public class NombaWalletTransferResponse {

    private String code;
    private String description;
    private TransferData data;

    @Data
    @NoArgsConstructor
    public static class TransferData {
        private BigDecimal amount;
        private BigDecimal fee;
        private String id;
        private String type;
        private String status;
        private String timeCreated;
        private TransferMeta meta;

        @Data
        @NoArgsConstructor
        public static class TransferMeta {
            private String merchantTxRef;
            private String apiClientId;
            private String apiAccountId;
            private String rrn;
        }
    }
}