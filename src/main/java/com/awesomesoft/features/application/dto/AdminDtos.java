package com.awesomesoft.features.application.dto;

import com.awesomesoft.features.domain.AdminRole;
import com.awesomesoft.features.domain.AuditOperation;
import tools.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.UUID;

public final class AdminDtos {

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    public record SessionUserResponse(String username, AdminRole role) {
    }

    public record AuditEntryResponse(String flagKey, AuditOperation operation, UUID workgroupId,
                                     JsonNode oldValue, JsonNode newValue, String actorUsername,
                                     LocalDateTime createdAt) {
    }

    public record CreateTokenRequest(@NotBlank @Size(max = 255) String name) {
    }

    public record TokenResponse(UUID id, String name, String tokenPrefix,
                                LocalDateTime createdAt, LocalDateTime revokedAt) {
    }

    /** {@code token} carries the plaintext exactly once, in the creation response. */
    public record TokenCreatedResponse(UUID id, String name, String tokenPrefix,
                                       LocalDateTime createdAt, String token) {
    }

    public record CreateAdminUserRequest(@NotBlank @Size(max = 255) String username,
                                         @NotBlank @Size(min = 8, max = 100) String password,
                                         @NotNull AdminRole role) {
    }

    /** Nulls mean "leave unchanged". */
    public record UpdateAdminUserRequest(AdminRole role, Boolean enabled,
                                         @Size(min = 8, max = 100) String password) {
    }

    public record AdminUserResponse(UUID id, String username, AdminRole role, boolean enabled) {
    }

    private AdminDtos() {
    }
}
