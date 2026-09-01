package com.awesomesoft.features.application.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public final class ApplicationDtos {

    public record CreateApplicationRequest(
            @NotBlank @Pattern(regexp = "^[a-z0-9-]{2,64}$") String slug,
            @NotBlank @Size(max = 255) String name) {
    }

    public record UpdateApplicationRequest(@NotBlank @Size(max = 255) String name) {
    }

    public record ApplicationResponse(UUID id, String slug, String name, long configVersion,
                                      long flagCount, LocalDateTime createdAt) {
    }

    private ApplicationDtos() {
    }
}
