package com.aura.ajo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateIntegratorRequest {

    @Schema(description = "Name of your app or platform. Shown in the API key registration confirmation.", example = "Grace Chapel App")
    @NotBlank
    @Size(min = 2, max = 100)
    private String name;
}
