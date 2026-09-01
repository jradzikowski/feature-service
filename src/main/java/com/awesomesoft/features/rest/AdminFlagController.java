package com.awesomesoft.features.rest;

import com.awesomesoft.features.application.AuditLogQueryService;
import com.awesomesoft.features.application.FlagFacade;
import com.awesomesoft.features.application.dto.AdminDtos.AuditEntryResponse;
import com.awesomesoft.features.application.dto.FlagDtos.CreateFlagRequest;
import com.awesomesoft.features.application.dto.FlagDtos.FlagDetailResponse;
import com.awesomesoft.features.application.dto.FlagDtos.FlagResponse;
import com.awesomesoft.features.application.dto.FlagDtos.OverrideResponse;
import com.awesomesoft.features.application.dto.FlagDtos.SetOverrideRequest;
import com.awesomesoft.features.application.dto.FlagDtos.UpdateFlagRequest;
import com.awesomesoft.features.application.dto.FlagDtos.WorkgroupOverrideResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin - Flags")
@RestController
@RequestMapping("/features-api/v1/admin/applications/{slug}")
@RequiredArgsConstructor
public class AdminFlagController {

    private final FlagFacade flagFacade;
    private final AuditLogQueryService auditLogQueryService;

    @Operation(summary = "List flags of an application")
    @GetMapping("/flags")
    public List<FlagResponse> list(@PathVariable String slug,
                                   @RequestParam(defaultValue = "false") boolean includeArchived) {
        return flagFacade.list(slug, includeArchived);
    }

    @Operation(summary = "Stale flags (past expires_at, or RELEASE/EXPERIMENT older than the budget)")
    @GetMapping("/flags/stale")
    public List<FlagResponse> listStale(@PathVariable String slug) {
        return flagFacade.listStale(slug);
    }

    @Operation(summary = "Create a flag")
    @PostMapping("/flags")
    public ResponseEntity<FlagDetailResponse> create(@PathVariable String slug,
                                                     @Valid @RequestBody CreateFlagRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(flagFacade.create(slug, request));
    }

    @Operation(summary = "Flag details with overrides")
    @GetMapping("/flags/{flagKey}")
    public FlagDetailResponse get(@PathVariable String slug, @PathVariable String flagKey) {
        return flagFacade.get(slug, flagKey);
    }

    @Operation(summary = "Update a flag (flagKey and valueType are immutable)")
    @PatchMapping("/flags/{flagKey}")
    public FlagDetailResponse update(@PathVariable String slug, @PathVariable String flagKey,
                                     @Valid @RequestBody UpdateFlagRequest request) {
        return flagFacade.update(slug, flagKey, request);
    }

    @Operation(summary = "Delete a flag (only when archived)")
    @DeleteMapping("/flags/{flagKey}")
    public ResponseEntity<Void> delete(@PathVariable String slug, @PathVariable String flagKey) {
        flagFacade.delete(slug, flagKey);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "Set or replace a workgroup override")
    @PutMapping("/flags/{flagKey}/overrides/{workgroupId}")
    public OverrideResponse setOverride(@PathVariable String slug, @PathVariable String flagKey,
                                        @PathVariable UUID workgroupId,
                                        @Valid @RequestBody SetOverrideRequest request) {
        return flagFacade.setOverride(slug, flagKey, workgroupId, request);
    }

    @Operation(summary = "Remove a workgroup override (the workgroup falls back to the default)")
    @DeleteMapping("/flags/{flagKey}/overrides/{workgroupId}")
    public ResponseEntity<Void> removeOverride(@PathVariable String slug, @PathVariable String flagKey,
                                               @PathVariable UUID workgroupId) {
        flagFacade.removeOverride(slug, flagKey, workgroupId);
        return ResponseEntity.noContent().build();
    }

    @Operation(summary = "All overrides of one workgroup across the application")
    @GetMapping("/overrides")
    public List<WorkgroupOverrideResponse> workgroupOverrides(@PathVariable String slug,
                                                              @RequestParam UUID workgroupId) {
        return flagFacade.listWorkgroupOverrides(slug, workgroupId);
    }

    @Operation(summary = "Configuration change journal")
    @GetMapping("/audit-log")
    public Page<AuditEntryResponse> auditLog(@PathVariable String slug,
                                             @RequestParam(required = false) String flagKey,
                                             @PageableDefault(size = 20) Pageable pageable) {
        return auditLogQueryService.find(slug, flagKey, pageable);
    }
}
