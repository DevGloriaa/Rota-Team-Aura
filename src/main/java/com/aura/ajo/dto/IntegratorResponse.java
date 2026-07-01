package com.aura.ajo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IntegratorResponse {

    private UUID id;
    private String name;
    /** Raw API key — returned ONLY at registration. Never stored; never retrievable again. */
    private String apiKey;
    private String note;
    private LocalDateTime createdAt;
}
