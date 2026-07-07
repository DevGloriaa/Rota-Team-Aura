package com.aura.ajo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AddMemberRequest {

    @Schema(description = "Member's full name.")
    @NotBlank
    @Size(min = 2, max = 100)
    private String name;

    @Schema(description = "Member's email address, used for identification.")
    @NotBlank
    @Email
    private String email;

    @Schema(required = false, description = "Optional. Member's phone number.")
    private String phone;

    @Schema(hidden = true)
    @Min(0)
    @Max(100)
    private int trustScore = 0;

    @Schema(hidden = true)
    private String nombaAccountId;

    @Schema(description = "Nigerian bank account number for receiving payouts — verified against Nomba at registration.")
    @NotBlank
    private String payoutAccountNumber;

    @Schema(description = "CBN bank code, e.g. \"058\" for GTBank, \"044\" for Access.")
    @NotBlank
    private String payoutBankCode;
}