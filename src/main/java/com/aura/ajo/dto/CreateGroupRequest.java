package com.aura.ajo.dto;

import com.aura.ajo.enums.Frequency;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@NoArgsConstructor
public class CreateGroupRequest {

    @NotBlank
    @Size(min = 2, max = 100)
    private String name;

    @NotNull
    @Positive
    private BigDecimal contributionAmount;

    @NotNull
    private Frequency frequency;

    /** Optional URL to receive outbound webhook push notifications for this group's events. */
    private String callbackUrl;
}