package com.awesomesoft.features.application.dto;

import tools.jackson.databind.JsonNode;
import com.awesomesoft.features.domain.ValueType;

import java.time.Instant;
import java.util.Map;

public record EvaluateResponse(String applicationSlug,
                               long configVersion,
                               Instant evaluatedAt,
                               Map<String, EvaluatedFlag> flags) {

    public record EvaluatedFlag(ValueType type, JsonNode value, Reason reason) {
    }

    public enum Reason {
        DEFAULT,
        WORKGROUP_OVERRIDE,
        LOCKED
    }
}
