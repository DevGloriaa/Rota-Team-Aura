package com.aura.ajo.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResolveQuarantineRequest {

    @NotBlank
    private String resolutionNote;
}
