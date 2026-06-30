package com.aura.ajo.dto;

import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
public class NombaCreateVirtualAccountResponse {

    private String code;
    private String description;
    private AccountData data;

    @Data
    @NoArgsConstructor
    public static class AccountData {
        private String createdAt;
        private String accountHolderId;
        private String accountRef;
        private String bvn;
        private String accountName;
        private String bankName;
        private String bankAccountNumber;
        private String bankAccountName;
        private String currency;
        private String callbackUrl;
        private boolean expired;
    }
}