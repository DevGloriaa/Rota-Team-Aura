package com.aura.ajo.dto;

import com.aura.ajo.enums.KycStatus;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateKycRequest {

    @NotNull
    private KycStatus kycStatus;
}
