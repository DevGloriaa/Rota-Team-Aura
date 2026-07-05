package com.aura.ajo.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class NombaUpdateVirtualAccountResponse {

    private String code;
    private String description;
    private boolean status;
}
