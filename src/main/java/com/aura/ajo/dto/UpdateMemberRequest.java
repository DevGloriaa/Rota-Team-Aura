package com.aura.ajo.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateMemberRequest {

    @Schema(required = false, description = "Optional. New display name for the member.")
    @Size(min = 2, max = 100)
    private String name;

    @Schema(required = false, description = "Optional. New email address for the member.")
    @Email
    private String email;
}
