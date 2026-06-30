package com.aura.ajo.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@JsonInclude(JsonInclude.Include.NON_NULL)
public class NombaCreateVirtualAccountRequest {

    private String accountRef;

    private String accountName;

    private String bvn;

    private String expiryDate;

    private Double expectedAmount;
}