package com.aura.ajo.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class CreateIntegratorRequest {

    @NotBlank
    @Size(min = 2, max = 100)
    private String name;
}
