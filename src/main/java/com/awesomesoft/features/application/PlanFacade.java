package com.awesomesoft.features.application;

import com.awesomesoft.features.application.dto.PlanDtos.AssignPlanRequest;
import com.awesomesoft.features.application.dto.PlanDtos.CreatePlanRequest;
import com.awesomesoft.features.application.dto.PlanDtos.PlanDetailResponse;
import com.awesomesoft.features.application.dto.PlanDtos.PlanFlagResponse;
import com.awesomesoft.features.application.dto.PlanDtos.PlanResponse;
import com.awesomesoft.features.application.dto.PlanDtos.SetPlanFlagRequest;
import com.awesomesoft.features.application.dto.PlanDtos.UpdatePlanRequest;
import com.awesomesoft.features.application.dto.PlanDtos.WorkgroupPlanResponse;
import com.awesomesoft.features.domain.ClientApplication;
import com.awesomesoft.features.domain.Flag;
import com.awesomesoft.features.domain.Plan;
import com.awesomesoft.features.domain.PlanFlag;
import com.awesomesoft.features.domain.Workgroup;
import com.awesomesoft.features.domain.WorkgroupPlan;
import com.awesomesoft.features.infrastructure.FlagJpaRepository;
import com.awesomesoft.features.infrastructure.PlanFlagJpaRepository;
import com.awesomesoft.features.infrastructure.PlanJpaRepository;
import com.awesomesoft.features.infrastructure.WorkgroupPlanJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PlanFacade {

    private final ApplicationFacade applicationFacade;
    private final WorkgroupFacade workgroupFacade;
    private final PlanJpaRepository planRepository;
    private final PlanFlagJpaRepository planFlagRepository;
    private final WorkgroupPlanJpaRepository workgroupPlanRepository;
    private final FlagJpaRepository flagRepository;
    private final JsonValues jsonValues;
    private final EvaluationService evaluationService;

    // ── Plans CRUD ────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public List<PlanResponse> list(String slug) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        List<Plan> plans = planRepository.findByApplicationIdOrderByName(app.getId());
        Map<UUID, Long> flagCounts = planFlagRepository.findAllByApplicationId(app.getId()).stream()
                .collect(Collectors.groupingBy(PlanFlag::getPlanId, Collectors.counting()));
        return plans.stream()
                .map(p -> toResponse(p, flagCounts.getOrDefault(p.getId(), 0L).intValue()))
                .toList();
    }

    @Transactional(readOnly = true)
    public PlanDetailResponse get(String slug, UUID planId) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        Plan plan = getPlan(app, planId);
        return toDetailResponse(plan, app);
    }

    @Transactional
    public PlanDetailResponse create(String slug, CreatePlanRequest request) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        if (planRepository.existsByApplicationIdAndName(app.getId(), request.name())) {
            throw new IllegalStateException("Plan '" + request.name() + "' already exists in '" + slug + "'");
        }
        Plan plan = planRepository.save(new Plan(app.getId(), request.name(), request.description()));
        return toDetailResponse(plan, app);
    }

    @Transactional
    public PlanDetailResponse update(String slug, UUID planId, UpdatePlanRequest request) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        Plan plan = getPlan(app, planId);
        if (request.name() != null && !request.name().equals(plan.getName())
                && planRepository.existsByApplicationIdAndName(app.getId(), request.name())) {
            throw new IllegalStateException("Plan '" + request.name() + "' already exists in '" + slug + "'");
        }
        plan.update(request.name(), request.description());
        invalidate(app);
        return toDetailResponse(plan, app);
    }

    @Transactional
    public void delete(String slug, UUID planId) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        Plan plan = getPlan(app, planId);
        planRepository.delete(plan);
        invalidate(app);
    }

    // ── Plan flags ────────────────────────────────────────────────────────────

    @Transactional
    public PlanFlagResponse setPlanFlag(String slug, UUID planId, String flagKey, SetPlanFlagRequest request) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        Plan plan = getPlan(app, planId);
        Flag flag = getFlag(app, flagKey);
        String value = jsonValues.validateAndSerialize(flag.getValueType(), request.value());
        PlanFlag pf = planFlagRepository.findByPlanIdAndFlagId(plan.getId(), flag.getId())
                .orElse(null);
        if (pf == null) {
            pf = planFlagRepository.save(new PlanFlag(plan.getId(), flag.getId(), value));
        } else {
            pf.setValue(value);
        }
        invalidate(app);
        return new PlanFlagResponse(flag.getFlagKey(), jsonValues.parse(value));
    }

    @Transactional
    public void removePlanFlag(String slug, UUID planId, String flagKey) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        Plan plan = getPlan(app, planId);
        Flag flag = getFlag(app, flagKey);
        PlanFlag pf = planFlagRepository.findByPlanIdAndFlagId(plan.getId(), flag.getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Flag '" + flagKey + "' is not in plan '" + plan.getName() + "'"));
        planFlagRepository.delete(pf);
        invalidate(app);
    }

    // ── Workgroup plan assignment ──────────────────────────────────────────────

    @Transactional
    public WorkgroupPlanResponse assignPlan(String slug, UUID workgroupId, AssignPlanRequest request) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        Workgroup wg = workgroupFacade.getById(workgroupId);
        Plan plan = getPlan(app, request.planId());
        WorkgroupPlan assignment = workgroupPlanRepository
                .findByWorkgroupIdAndApplicationId(workgroupId, app.getId())
                .orElse(null);
        if (assignment == null) {
            assignment = workgroupPlanRepository.save(new WorkgroupPlan(workgroupId, plan.getId(), app.getId()));
        } else {
            assignment.reassign(plan.getId());
        }
        invalidate(app);
        return new WorkgroupPlanResponse(workgroupId, wg.getName(), plan.getId(), plan.getName(), assignment.getAssignedAt());
    }

    @Transactional
    public void unassignPlan(String slug, UUID workgroupId) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        WorkgroupPlan assignment = workgroupPlanRepository
                .findByWorkgroupIdAndApplicationId(workgroupId, app.getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Workgroup '" + workgroupId + "' has no plan in '" + slug + "'"));
        workgroupPlanRepository.delete(assignment);
        invalidate(app);
    }

    @Transactional(readOnly = true)
    public WorkgroupPlanResponse getAssignment(String slug, UUID workgroupId) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        WorkgroupPlan assignment = workgroupPlanRepository
                .findByWorkgroupIdAndApplicationId(workgroupId, app.getId())
                .orElseThrow(() -> new NoSuchElementException(
                        "Workgroup '" + workgroupId + "' has no plan in '" + slug + "'"));
        Plan plan = planRepository.findById(assignment.getPlanId())
                .orElseThrow(() -> new NoSuchElementException("Plan not found"));
        Workgroup wg = workgroupFacade.getById(workgroupId);
        return new WorkgroupPlanResponse(workgroupId, wg.getName(), plan.getId(), plan.getName(), assignment.getAssignedAt());
    }

    // ── helpers ───────────────────────────────────────────────────────────────

    private Plan getPlan(ClientApplication app, UUID planId) {
        return planRepository.findByIdAndApplicationId(planId, app.getId())
                .orElseThrow(() -> new NoSuchElementException("Plan '" + planId + "' not found in '" + app.getSlug() + "'"));
    }

    private Flag getFlag(ClientApplication app, String flagKey) {
        return flagRepository.findByApplicationIdAndFlagKey(app.getId(), flagKey)
                .orElseThrow(() -> new NoSuchElementException(
                        "Flag '" + flagKey + "' not found in '" + app.getSlug() + "'"));
    }

    private void invalidate(ClientApplication app) {
        evaluationService.invalidateAfterCommit(app.getId());
    }

    private PlanResponse toResponse(Plan p, int flagCount) {
        return new PlanResponse(p.getId(), p.getName(), p.getDescription(), flagCount, p.getCreatedAt(), p.getUpdatedAt());
    }

    private PlanDetailResponse toDetailResponse(Plan plan, ClientApplication app) {
        Map<UUID, String> flagKeyById = flagRepository.findByApplicationIdOrderByFlagKey(app.getId()).stream()
                .collect(Collectors.toMap(Flag::getId, Flag::getFlagKey));
        List<PlanFlagResponse> flags = planFlagRepository.findByPlanId(plan.getId()).stream()
                .map(pf -> new PlanFlagResponse(flagKeyById.getOrDefault(pf.getFlagId(), "?"), jsonValues.parse(pf.getValue())))
                .sorted(Comparator.comparing(PlanFlagResponse::flagKey))
                .toList();
        return new PlanDetailResponse(plan.getId(), plan.getName(), plan.getDescription(),
                plan.getCreatedAt(), plan.getUpdatedAt(), flags);
    }
}
