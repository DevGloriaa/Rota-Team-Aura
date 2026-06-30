package com.aura.ajo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ProvisionResponse {

    private UUID groupId;
    private int membersProvisioned;
    private boolean groupPoolProvisioned;
    private List<MemberResponse> members;
}