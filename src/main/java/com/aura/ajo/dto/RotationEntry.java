package com.aura.ajo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RotationEntry {

    private int position;
    private UUID memberId;
    private String memberName;
    private int trustScore;
    private boolean hasCollected;
}