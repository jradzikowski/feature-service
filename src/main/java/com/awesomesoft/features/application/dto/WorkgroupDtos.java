package com.awesomesoft.features.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public final class WorkgroupDtos {

    public record CreateWorkgroupRequest(
            @NotNull UUID id,
            @NotBlank @Size(max = 255) String name) {
    }

    public record UpdateWorkgroupRequest(
            @NotBlank @Size(max = 255) String name) {
    }

    public record WorkgroupResponse(UUID id, String name, LocalDateTime createdAt, LocalDateTime updatedAt) {
    }

    private WorkgroupDtos() {
    }
}
