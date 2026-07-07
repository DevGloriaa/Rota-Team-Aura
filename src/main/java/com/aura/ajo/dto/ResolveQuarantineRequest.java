package com.aura.ajo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class ResolveQuarantineRequest {

    @Schema(description = "Free-text note explaining how this misdirected payment was resolved.", example = "Refunded manually to sender; not applied to any group.")
    @NotBlank
    private String resolutionNote;
}
