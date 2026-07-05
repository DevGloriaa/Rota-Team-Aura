package com.aura.ajo.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
public class UpdateMemberRequest {

    @Size(min = 2, max = 100)
    private String name;

    @Email
    private String email;
}
