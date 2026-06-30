package com.aura.ajo.controller;

import com.aura.ajo.dto.AddMemberRequest;
import com.aura.ajo.dto.ApiResponse;
import com.aura.ajo.dto.CreateGroupRequest;
import com.aura.ajo.dto.GroupHealthResponse;
import com.aura.ajo.dto.GroupResponse;
import com.aura.ajo.dto.MemberResponse;
import com.aura.ajo.dto.PayoutResponse;
import com.aura.ajo.dto.ProvisionResponse;
import com.aura.ajo.dto.RotationEntry;
import com.aura.ajo.dto.TrustScoreBreakdown;
import com.aura.ajo.dto.UpcomingDueResponse;
import com.aura.ajo.service.GroupService;
import com.aura.ajo.service.PayoutService;
import com.aura.ajo.service.TrustScoringService;
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
public class GroupController {

    private final GroupService groupService;
    private final PayoutService payoutService;
    private final TrustScoringService trustScoringService;

    // ── Groups ────────────────────────────────────────────────────────────────

    @PostMapping("/groups")
    public ResponseEntity<ApiResponse<GroupResponse>> createGroup(
            @Valid @RequestBody CreateGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Group created", groupService.createGroup(request)));
    }

    @GetMapping("/groups/{groupId}")
    public ResponseEntity<ApiResponse<GroupResponse>> getGroup(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getGroup(groupId)));
    }

    // ── Members ───────────────────────────────────────────────────────────────

    @PostMapping("/groups/{groupId}/members")
    public ResponseEntity<ApiResponse<MemberResponse>> addMember(
            @PathVariable UUID groupId,
            @Valid @RequestBody AddMemberRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED)
            .body(ApiResponse.ok("Member added", groupService.addMember(groupId, request)));
    }

    @GetMapping("/groups/{groupId}/members")
    public ResponseEntity<ApiResponse<List<MemberResponse>>> getMembers(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getMembers(groupId)));
    }

    // ── Provisioning ──────────────────────────────────────────────────────────

    @PostMapping("/groups/{groupId}/provision")
    public ResponseEntity<ApiResponse<ProvisionResponse>> provision(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(
            ApiResponse.ok("Virtual accounts provisioned", groupService.provisionVirtualAccounts(groupId)));
    }

    // ── Activation ────────────────────────────────────────────────────────────

    @PostMapping("/groups/{groupId}/activate")
    public ResponseEntity<ApiResponse<GroupResponse>> activate(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(
            ApiResponse.ok("Group activated and rotation locked", groupService.activateGroup(groupId)));
    }

    @GetMapping("/groups/{groupId}/rotation")
    public ResponseEntity<ApiResponse<List<RotationEntry>>> getRotation(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getRotation(groupId)));
    }

    // ── Ledger ────────────────────────────────────────────────────────────────

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
    @GetMapping("/groups/{groupId}/upcoming-dues")
    public ResponseEntity<ApiResponse<UpcomingDueResponse>> getUpcomingDues(
            @PathVariable UUID groupId) {
        return ResponseEntity.ok(ApiResponse.ok(groupService.getUpcomingDues(groupId)));
    }

    // ── Phase 4: Health + Trust score ─────────────────────────────────────────

    /**
     * Group health dashboard — shows each member's contribution status for the
     * current cycle, pool balance, at-risk flag, and who's next in rotation.
     *
     *   curl -s localhost:8080/api/v1/groups/{groupId}/health
     */
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
    @GetMapping("/members/{memberId}/trust-score")
    public ResponseEntity<ApiResponse<TrustScoreBreakdown>> getTrustScore(
            @PathVariable UUID memberId) {
        return ResponseEntity.ok(ApiResponse.ok(trustScoringService.getScoreBreakdown(memberId)));
    }

}