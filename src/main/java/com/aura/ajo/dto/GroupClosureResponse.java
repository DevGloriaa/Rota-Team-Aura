package com.aura.ajo.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GroupClosureResponse {

    private UUID groupId;
    private String groupName;
    private String status;
    private int finalCycle;
    private BigDecimal finalPoolBalance;
    private List<MemberStatementResponse> memberStatements;
}
