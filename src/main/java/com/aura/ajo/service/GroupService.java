package com.aura.ajo.service;

import com.aura.ajo.dto.AddMemberRequest;
import com.aura.ajo.dto.CreateGroupRequest;
import com.aura.ajo.dto.GroupHealthResponse;
import com.aura.ajo.dto.GroupReportResponse;
import com.aura.ajo.dto.GroupResponse;
import com.aura.ajo.dto.MemberResponse;
import com.aura.ajo.dto.MemberStatementResponse;
import com.aura.ajo.dto.ProvisionResponse;
import com.aura.ajo.dto.RotationEntry;
import com.aura.ajo.dto.SimulateContributionRequest;
import com.aura.ajo.dto.UpcomingDueResponse;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public interface GroupService {

    GroupResponse createGroup(CreateGroupRequest request);

    List<GroupResponse> listGroups();

    GroupResponse getGroup(UUID groupId);

    MemberResponse addMember(UUID groupId, AddMemberRequest request);

    List<MemberResponse> getMembers(UUID groupId);

    ProvisionResponse provisionVirtualAccounts(UUID groupId);

    GroupResponse activateGroup(UUID groupId);

    List<RotationEntry> getRotation(UUID groupId);

    BigDecimal getPoolBalance(UUID groupId);

    Map<String, Object> simulateContribution(SimulateContributionRequest request);

    GroupHealthResponse getGroupHealth(UUID groupId);

    UpcomingDueResponse getUpcomingDues(UUID groupId);

    MemberStatementResponse getMemberStatement(UUID groupId, UUID memberId);

    GroupReportResponse getGroupReport(UUID groupId);
}