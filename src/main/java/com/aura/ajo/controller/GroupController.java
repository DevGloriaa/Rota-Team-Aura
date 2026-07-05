package com.aura.ajo.controller;

import com.aura.ajo.dto.AddMemberRequest;
import com.aura.ajo.dto.ApiResponse;
import com.aura.ajo.dto.CreateGroupRequest;
import com.aura.ajo.dto.GroupClosureResponse;
import com.aura.ajo.dto.GroupHealthResponse;
import com.aura.ajo.dto.GroupReportResponse;
import com.aura.ajo.dto.GroupResponse;
import com.aura.ajo.dto.KycUpdateResponse;
import com.aura.ajo.dto.MemberResponse;
import com.aura.ajo.dto.MemberStatementResponse;
import com.aura.ajo.dto.PayoutResponse;
import com.aura.ajo.dto.ProvisionResponse;
import com.aura.ajo.dto.RotationEntry;
import com.aura.ajo.dto.TrustScoreBreakdown;
import com.aura.ajo.dto.UpcomingDueResponse;
import com.aura.ajo.dto.UpdateKycRequest;
import com.aura.ajo.dto.UpdateMemberRequest;
import com.aura.ajo.service.GroupService;
import com.aura.ajo.service.PayoutService;
import com.aura.ajo.service.TrustScoringService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Single REST controller for the rotating savings group API.
 *
 * Base path: /api/v1
 *
 * curl examples:
 *
 *   # Create group
 *   curl -s -X POST localhost:8080/api/v1/groups \
 *     -H 'Content-Type: application/json' \
 *     -d '{"name":"Circle A","contributionAmount":10000,"frequency":"MONTHLY"}'
 *
 *   # Add member  (replace {groupId})
 *   curl -s -X POST localhost:8080/api/v1/groups/{groupId}/members \
 *     -H 'Content-Type: application/json' \
 *     -d '{"name":"Ada Obi","email":"ada@example.com","trustScore":80}'
 *
 *   # Provision virtual accounts
 *   curl -s -X POST localhost:8080/api/v1/groups/{groupId}/provision
 *
 *   # Activate (locks rotation)
 *   curl -s -X POST localhost:8080/api/v1/groups/{groupId}/activate
 *
 *   # View trust-ordered rotation
 *   curl -s localhost:8080/api/v1/groups/{groupId}/rotation
 *
 *   # Pool balance
 *   curl -s localhost:8080/api/v1/groups/{groupId}/balance
 *
 *   # Simulate a contribution (dev only — supply same requestId twice to test idempotency)
 *   curl -s -X POST localhost:8080/api/v1/test/simulate-contribution \
 *     -H 'Content-Type: application/json' \
 *     -d '{"groupId":"{groupId}","memberId":"{memberId}","amount":10000,"requestId":"test-req-1"}'
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
@Tag(name = "Groups")
public class GroupController {

    private final GroupService groupService;
    private final PayoutService payoutService;
    private final TrustScoringService trustScoringService;

    // ── Groups ────────────────────────────────────────────────────────────────

    @Operation(summary = "Create a savings group")
    @PostMapping("/groups")
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
            @Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Group created", groupService.createGroup(request)));
    }

    @Operation(summary = "List your groups")
    @GetMapping("/groups")
    public ResponseEntity<ApiResponse<List<GroupResponse>>> listGroups() {
        return ResponseEntity.ok(ApiResponse.ok(groupService.listGroups()));
    }

    @Operation(summary = "Get group details")
    @GetMapping("/groups/{groupId}")
    public ResponseEntity<ApiResponse<GroupResponse>> getGroup(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getGroup(groupId)));
    }

    /**
     * Explicitly closes a group: marks it COMPLETED and expires every member's Nomba
     * virtual account. Returns a closure summary with each member's final statement.
     *
     *   curl -s -X POST localhost:8080/api/v1/groups/{groupId}/close
     */
    @Operation(summary = "Close a group and expire member virtual accounts")
    @PostMapping("/groups/{groupId}/close")
    public ResponseEntity<ApiResponse<GroupClosureResponse>> closeGroup(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok("Group closed", groupService.closeGroup(groupId)));
    }

    // ── Members ───────────────────────────────────────────────────────────────

    @Operation(summary = "Add a member to a group")
    @PostMapping("/groups/{groupId}/members")
    public ResponseEntity<ApiResponse<MemberResponse>> addMember(
            @PathVariable UUID groupId,
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Member added", groupService.addMember(groupId, request)));
    }

    @Operation(summary = "List members in a group")
    @GetMapping("/groups/{groupId}/members")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> getMembers(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getMembers(groupId)));
    }

    /**
     * Renames a member and/or updates their email. Contribution history, trust score,
     * and rotation position are all preserved. If the member has a provisioned virtual
     * account, its display name is synced to Nomba.
     *
     *   curl -s -X PUT localhost:8080/api/v1/groups/{groupId}/members/{memberId} \
     *     -H 'Content-Type: application/json' \
     *     -d '{"name":"Ada N. Obi"}'
     */
    @Operation(summary = "Update a member's name and/or email")
    @PutMapping("/groups/{groupId}/members/{memberId}")
    public ResponseEntity<ApiResponse<MemberResponse>> updateMember(
            @PathVariable UUID groupId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateMemberRequest request) {
        return ResponseEntity.ok(
            ApiResponse.ok("Member updated", groupService.updateMember(groupId, memberId, request)));
    }

    /**
     * Updates a member's KYC tier and recalculates their trust score's cold-start base.
     * For FORMING groups, rotationAffected is true since the new score can shift rotation
     * position at activation. For ACTIVE groups the locked rotation order never changes.
     *
     *   curl -s -X PUT localhost:8080/api/v1/groups/{groupId}/members/{memberId}/kyc \
     *     -H 'Content-Type: application/json' \
     *     -d '{"kycStatus":"VERIFIED"}'
     */
    @Operation(summary = "Update a member's KYC tier and recalculate trust score")
    @PutMapping("/groups/{groupId}/members/{memberId}/kyc")
    public ResponseEntity<ApiResponse<KycUpdateResponse>> updateMemberKyc(
            @PathVariable UUID groupId,
            @PathVariable UUID memberId,
            @Valid @RequestBody UpdateKycRequest request) {
        return ResponseEntity.ok(
            ApiResponse.ok("KYC tier updated", groupService.updateMemberKyc(groupId, memberId, request)));
    }

    // ── Provisioning ──────────────────────────────────────────────────────────

    @Operation(summary = "Provision Nomba virtual accounts for all members")
    @PostMapping("/groups/{groupId}/provision")
    public ResponseEntity<ApiResponse<ProvisionResponse>> provision(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(
            ApiResponse.ok("Virtual accounts provisioned", groupService.provisionVirtualAccounts(groupId)));
    }

    // ── Activation ────────────────────────────────────────────────────────────

    @Operation(summary = "Activate group and lock rotation")
    @PostMapping("/groups/{groupId}/activate")
    public ResponseEntity<ApiResponse<GroupResponse>> activate(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(
            ApiResponse.ok("Group activated and rotation locked", groupService.activateGroup(groupId)));
    }

    @Operation(summary = "Get the locked rotation order")
    @GetMapping("/groups/{groupId}/rotation")
    public ResponseEntity<ApiResponse<List<RotationEntry>>> getRotation(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getRotation(groupId)));
    }

    // ── Ledger ────────────────────────────────────────────────────────────────

    @Operation(summary = "Get pool balance for a group")
    @GetMapping("/groups/{groupId}/balance")
    public ResponseEntity<ApiResponse<Map<String, Object>>> getPoolBalance(
            @PathVariable UUID groupId) {
        BigDecimal balance = groupService.getPoolBalance(groupId);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "groupId", groupId,
            "poolBalance", balance
        )));
    }

    // ── Payouts ───────────────────────────────────────────────────────────────

    /**
     * Returns all executed payouts for a group in cycle order.
     * Also readable in conjunction with GET /rotation to see who collects next.
     *
     *   curl -s localhost:8080/api/v1/groups/{groupId}/payouts
     */
    @Operation(summary = "List payout history for a group")
    @GetMapping("/groups/{groupId}/payouts")
    public ResponseEntity<ApiResponse<List<PayoutResponse>>> getPayouts(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(payoutService.getPayoutHistory(groupId)));
    }

    /**
     * Manually triggers the payout for a specific cycle. Use after funding all
     * contributions via simulate-contribution (or live webhooks) to test the payout path.
     * Throws 409 if the payout was already executed for this cycle.
     *
     *   curl -s -X POST localhost:8080/api/v1/groups/{groupId}/cycles/1/trigger-payout
     */
    @Operation(summary = "Manually trigger payout for a cycle")
    @PostMapping("/groups/{groupId}/cycles/{cycleNumber}/trigger-payout")
    public ResponseEntity<ApiResponse<Map<String, Object>>> triggerPayout(
            @PathVariable UUID groupId,
            @PathVariable int cycleNumber) {
        payoutService.triggerPayoutForCycle(groupId, cycleNumber);
        return ResponseEntity.ok(ApiResponse.ok(Map.of(
            "groupId", groupId,
            "cycleNumber", cycleNumber,
            "message", "Payout executed"
        )));
    }

    // ── Events / upcoming dues ────────────────────────────────────────────────

    /**
     * Returns all non-PAID contributions whose periodEnd is on or after today,
     * ordered by deadline ascending. Use this to drive reminder UI and push alerts.
     * daysUntilDue is negative for overdue contributions still awaiting payout.
     *
     *   curl -s localhost:8080/api/v1/groups/{groupId}/upcoming-dues
     */
    @Operation(summary = "Get upcoming contribution deadlines")
    @GetMapping("/groups/{groupId}/upcoming-dues")
    public ResponseEntity<ApiResponse<UpcomingDueResponse>> getUpcomingDues(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getUpcomingDues(groupId)));
    }

    // ── Statement + reporting ─────────────────────────────────────────────────

    /**
     * Full contribution history and standing for one member: every cycle's expected vs
     * received amount and status, rotation position, hasCollected, trustScore, owedAmount.
     *
     *   curl -s localhost:8080/api/v1/groups/{groupId}/members/{memberId}/statement
     */
    @Operation(summary = "Get a member's contribution statement")
    @GetMapping("/groups/{groupId}/members/{memberId}/statement")
    public ResponseEntity<ApiResponse<MemberStatementResponse>> getMemberStatement(
            @PathVariable UUID groupId,
            @PathVariable UUID memberId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getMemberStatement(groupId, memberId)));
    }

    /**
     * Group-level report: status, current cycle, pool balance, per-member funding status
     * for the current cycle, full payout history, and rotation order (who collects next).
     *
     *   curl -s localhost:8080/api/v1/groups/{groupId}/report
     */
    @Operation(summary = "Get group funding status and payout history")
    @GetMapping("/groups/{groupId}/report")
    public ResponseEntity<ApiResponse<GroupReportResponse>> getGroupReport(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getGroupReport(groupId)));
    }

    // ── Phase 4: Health + Trust score ─────────────────────────────────────────

    /**
     * Group health dashboard — shows each member's contribution status for the
     * current cycle, pool balance, at-risk flag, and who's next in rotation.
     *
     *   curl -s localhost:8080/api/v1/groups/{groupId}/health
     */
    @Operation(summary = "Get group health dashboard")
    @GetMapping("/groups/{groupId}/health")
    public ResponseEntity<ApiResponse<GroupHealthResponse>> getHealth(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getGroupHealth(groupId)));
    }

    /**
     * Returns a member's trust score with the full breakdown of contributing factors.
     * Uses the memberId UUID (from POST /members response).
     *
     *   curl -s localhost:8080/api/v1/members/{memberId}/trust-score
     */
    @Operation(summary = "Get a member's trust score breakdown")
    @GetMapping("/members/{memberId}/trust-score")
    public ResponseEntity<ApiResponse<TrustScoreBreakdown>> getTrustScore(
            @PathVariable UUID memberId) {
        return ResponseEntity.ok(ApiResponse.ok(trustScoringService.getScoreBreakdown(memberId)));
    }

}