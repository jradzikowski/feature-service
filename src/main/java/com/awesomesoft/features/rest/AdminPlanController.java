package com.awesomesoft.features.rest;

import com.awesomesoft.features.application.PlanFacade;
import com.awesomesoft.features.application.dto.PlanDtos.AssignPlanRequest;
import com.awesomesoft.features.application.dto.PlanDtos.CreatePlanRequest;
import com.awesomesoft.features.application.dto.PlanDtos.PlanDetailResponse;
import com.awesomesoft.features.application.dto.PlanDtos.PlanFlagResponse;
import com.awesomesoft.features.application.dto.PlanDtos.PlanResponse;
import com.awesomesoft.features.application.dto.PlanDtos.SetPlanFlagRequest;
import com.awesomesoft.features.application.dto.PlanDtos.UpdatePlanRequest;
import com.awesomesoft.features.application.dto.PlanDtos.WorkgroupPlanResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

import static com.awesomesoft.features.config.OpenApiConfig.BASIC_AUTH;

@Tag(name = "Admin - Plans")
@SecurityRequirement(name = BASIC_AUTH)
@RestController
@RequestMapping("/features-api/v1/admin/applications/{slug}")
@RequiredArgsConstructor
public class AdminPlanController {

    private final PlanFacade planFacade;

    // ── Plans ─────────────────────────────────────────────────────────────────

    @Operation(summary = "List plans of an application")
    @GetMapping("/plans")
    public List<PlanResponse> list(@PathVariable String slug) {
        return planFacade.list(slug);
    }

    @Operation(summary = "Create a plan")
    @PostMapping("/plans")
    public ResponseEntity<PlanDetailResponse> create(@PathVariable String slug,
                                                     @Valid @RequestBody CreatePlanRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(planFacade.create(slug, request));
    }

    @Operation(summary = "Plan details with flag values")
    @GetMapping("/plans/{planId}")
    public PlanDetailResponse get(@PathVariable String slug, @PathVariable UUID planId) {
        return planFacade.get(slug, planId);
    }

    @Operation(summary = "Update plan name/description")
    @PatchMapping("/plans/{planId}")
    public PlanDetailResponse update(@PathVariable String slug, @PathVariable UUID planId,
                                     @Valid @RequestBody UpdatePlanRequest request) {
        return planFacade.update(slug, planId, request);
    }

    @Operation(summary = "Delete a plan (removes all assignments)")
    @DeleteMapping("/plans/{planId}")
    public ResponseEntity<Void> delete(@PathVariable String slug, @PathVariable UUID planId) {
        planFacade.delete(slug, planId);
        return ResponseEntity.noContent().build();
    }

    // ── Plan flags ────────────────────────────────────────────────────────────

    @Operation(summary = "Set a flag value in a plan")
    @PutMapping("/plans/{planId}/flags/{flagKey}")
    public PlanFlagResponse setPlanFlag(@PathVariable String slug, @PathVariable UUID planId,
                                        @PathVariable String flagKey,
                                        @Valid @RequestBody SetPlanFlagRequest request) {
        return planFacade.setPlanFlag(slug, planId, flagKey, request);
    }

    @Operation(summary = "Remove a flag from a plan (workgroups on this plan fall back to default)")
    @DeleteMapping("/plans/{planId}/flags/{flagKey}")
    public ResponseEntity<Void> removePlanFlag(@PathVariable String slug, @PathVariable UUID planId,
                                               @PathVariable String flagKey) {
        planFacade.removePlanFlag(slug, planId, flagKey);
        return ResponseEntity.noContent().build();
    }

    // ── Workgroup assignment ──────────────────────────────────────────────────

    @Operation(summary = "Assign (or replace) a plan for a workgroup in this application")
    @PutMapping("/workgroups/{workgroupId}/plan")
    public WorkgroupPlanResponse assignPlan(@PathVariable String slug, @PathVariable UUID workgroupId,
                                            @Valid @RequestBody AssignPlanRequest request) {
        return planFacade.assignPlan(slug, workgroupId, request);
    }

    @Operation(summary = "Remove plan assignment (workgroup falls back to defaults)")
    @DeleteMapping("/workgroups/{workgroupId}/plan")
    public ResponseEntity<Void> unassignPlan(@PathVariable String slug, @PathVariable UUID workgroupId) {
        planFacade.unassignPlan(slug, workgroupId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Get current plan assignment for a workgroup")
    @GetMapping("/workgroups/{workgroupId}/plan")
    public WorkgroupPlanResponse getAssignment(@PathVariable String slug, @PathVariable UUID workgroupId) {
        return planFacade.getAssignment(slug, workgroupId);
    }
}
