package com.awesomesoft.features.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import tools.jackson.databind.JsonNode;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class PlanDtos {

    public record CreatePlanRequest(
            @NotBlank @Size(max = 255) String name,
            String description) {
    }

    public record UpdatePlanRequest(
            @Size(max = 255) String name,
            String description) {
    }

    public record PlanResponse(UUID id, String name, String description, int flagCount,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record PlanDetailResponse(UUID id, String name, String description,
                                     LocalDateTime createdAt, LocalDateTime updatedAt,
                                     List<PlanFlagResponse> flags) {
    }

    public record SetPlanFlagRequest(@NotNull JsonNode value) {
    }

    public record PlanFlagResponse(String flagKey, JsonNode value) {
    }

    public record AssignPlanRequest(@NotNull UUID planId) {
    }

    public record WorkgroupPlanResponse(UUID workgroupId, String workgroupName, UUID planId, String planName,
                                        LocalDateTime assignedAt) {
    }

    private PlanDtos() {
    }
}
