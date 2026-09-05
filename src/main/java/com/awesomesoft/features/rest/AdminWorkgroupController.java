package com.awesomesoft.features.rest;

import com.awesomesoft.features.application.WorkgroupFacade;
import com.awesomesoft.features.application.dto.WorkgroupDtos.CreateWorkgroupRequest;
import com.awesomesoft.features.application.dto.WorkgroupDtos.UpdateWorkgroupRequest;
import com.awesomesoft.features.application.dto.WorkgroupDtos.WorkgroupResponse;
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

@Tag(name = "Admin - Workgroups")
@SecurityRequirement(name = BASIC_AUTH)
@RestController
@RequestMapping("/features-api/v1/admin/workgroups")
@RequiredArgsConstructor
public class AdminWorkgroupController {

    private final WorkgroupFacade workgroupFacade;

    @Operation(summary = "List workgroups, optionally filtered by name")
    @GetMapping
    public List<WorkgroupResponse> list(@RequestParam(required = false) String name) {
        return workgroupFacade.list(name);
    }

    @Operation(summary = "Register a workgroup")
    @PostMapping
    public ResponseEntity<WorkgroupResponse> create(@Valid @RequestBody CreateWorkgroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(workgroupFacade.create(request));
    }

    @Operation(summary = "Get a workgroup")
    @GetMapping("/{id}")
    public WorkgroupResponse get(@PathVariable UUID id) {
        return workgroupFacade.get(id);
    }

    @Operation(summary = "Rename a workgroup")
    @PatchMapping("/{id}")
    public WorkgroupResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateWorkgroupRequest request) {
        return workgroupFacade.update(id, request);
    }

    @Operation(summary = "Delete a workgroup")
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable UUID id) {
        workgroupFacade.delete(id);
        return ResponseEntity.noContent().build();
    }
}
