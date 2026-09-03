package com.awesomesoft.features.rest;

import com.awesomesoft.features.application.AdminUserFacade;
import com.awesomesoft.features.application.dto.AdminDtos.AdminUserResponse;
import com.awesomesoft.features.application.dto.AdminDtos.CreateAdminUserRequest;
import com.awesomesoft.features.application.dto.AdminDtos.UpdateAdminUserRequest;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import static com.awesomesoft.features.config.OpenApiConfig.BASIC_AUTH;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin - Users", description = "Panel accounts (ADMIN role required)")
@SecurityRequirement(name = BASIC_AUTH)
@RestController
@RequestMapping("/features-api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final AdminUserFacade adminUserFacade;

    @Operation(summary = "List panel users")
    @GetMapping
    public List<AdminUserResponse> list() {
        return adminUserFacade.list();
    }

    @Operation(summary = "Create a panel user")
    @PostMapping
    public ResponseEntity<AdminUserResponse> create(@Valid @RequestBody CreateAdminUserRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(adminUserFacade.create(request));
    }

    @Operation(summary = "Update role/enabled/password of a panel user")
    @PatchMapping("/{id}")
    public AdminUserResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateAdminUserRequest request) {
        return adminUserFacade.update(id, request);
    }
}
