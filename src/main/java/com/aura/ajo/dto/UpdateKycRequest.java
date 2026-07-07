package com.aura.ajo.dto;

import com.aura.ajo.enums.KycStatus;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateKycRequest {

    @Schema(description = "New KYC tier for the member. PENDING/VERIFIED/REJECTED affects the trust score's cold-start base.", example = "VERIFIED")
    @NotNull
    private KycStatus kycStatus;
}
