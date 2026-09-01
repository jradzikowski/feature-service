package com.awesomesoft.features.application.dto;

import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record EvaluateRequest(@NotNull Context context) {

    /** userId/attributes are accepted but unused for now (reserved for user overrides/segments). */
    public record Context(UUID workgroupId, String userId, JsonNode attributes) {
    }
}
