package com.awesomesoft.features.application.dto;

import com.awesomesoft.features.domain.FlagKind;
import com.awesomesoft.features.domain.ValueType;
import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

public final class FlagDtos {

    public static final String FLAG_KEY_PATTERN = "^[a-z0-9]+(?:[.-][a-z0-9]+)*$";

    public record CreateFlagRequest(
            @NotBlank @Pattern(regexp = FLAG_KEY_PATTERN) @Size(max = 128) String flagKey,
            @NotBlank @Size(max = 255) String name,
            String description,
            @NotNull ValueType valueType,
            @NotNull JsonNode defaultValue,
            @NotNull FlagKind flagKind,
            LocalDate expiresAt,
            @Size(max = 255) String owner) {
    }

    /**
     * Nulls mean "leave unchanged"; flagKey and valueType are immutable and absent on purpose.
     * Because of that convention, expiresAt cannot be cleared by sending null — set
     * clearExpiresAt=true to make the flag never expire (it wins over expiresAt).
     */
    public record UpdateFlagRequest(
            @Size(max = 255) String name,
            String description,
            JsonNode defaultValue,
            Boolean locked,
            Boolean archived,
            LocalDate expiresAt,
            Boolean clearExpiresAt,
            @Size(max = 255) String owner) {
    }

    public record FlagResponse(String flagKey, String name, String description, ValueType valueType,
                               JsonNode defaultValue, FlagKind flagKind, boolean locked, boolean archived,
                               LocalDate expiresAt, String owner, long overrideCount,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    public record FlagDetailResponse(String flagKey, String name, String description, ValueType valueType,
                                     JsonNode defaultValue, FlagKind flagKind, boolean locked, boolean archived,
                                     LocalDate expiresAt, String owner, long overrideCount,
                                     LocalDateTime createdAt, LocalDateTime updatedAt,
                                     List<OverrideResponse> overrides) {
    }

    public record SetOverrideRequest(@NotNull JsonNode value, @Size(max = 512) String note) {
    }

    public record OverrideResponse(UUID workgroupId, JsonNode value, String note, LocalDateTime updatedAt) {
    }

    /** Row of the "what has this workgroup changed" view. */
    public record WorkgroupOverrideResponse(String flagKey, JsonNode value, String note, LocalDateTime updatedAt) {
    }

    private FlagDtos() {
    }
}
