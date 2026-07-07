package com.aura.ajo.dto;

import com.aura.ajo.enums.Frequency;
import io.swagger.v3.oas.annotations.media.Schema;
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

    @Schema(description = "Name of the savings group.")
    @NotBlank
    @Size(min = 2, max = 100)
    private String name;

    @Schema(description = "Amount each member contributes per cycle.")
    @NotNull
    @Positive
    private BigDecimal contributionAmount;

    @Schema(description = "How often the group collects contributions.")
    @NotNull
    private Frequency frequency;

    @Schema(required = false, description = "Optional. URL to receive outbound webhook push notifications for this group's events.")
    private String callbackUrl;
}