package com.aura.ajo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class VirtualAccountResponse {

    private UUID id;
    private String accountRef;
    private String bankAccountNumber;
    private String bankAccountName;
    private String bankName;
    private String type;
}