package com.aura.ajo.dto;

import jakarta.validation.constraints.*;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class AddMemberRequest {

    @NotBlank
    @Size(min = 2, max = 100)
    private String name;

    @NotBlank
    @Email
    private String email;

    private String phone;

    @Min(0)
    @Max(100)
    private int trustScore = 0;

    private String nombaAccountId;

    /** Nigerian bank account number for receiving payouts — verified against Nomba at registration. */
    @NotBlank
    private String payoutAccountNumber;

    /** CBN bank code (e.g. "058" for GTBank, "044" for Access). */
    @NotBlank
    private String payoutBankCode;
}