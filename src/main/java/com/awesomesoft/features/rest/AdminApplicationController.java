package com.awesomesoft.features.rest;

import com.awesomesoft.features.application.ApplicationFacade;
import com.awesomesoft.features.application.dto.ApplicationDtos.ApplicationResponse;
import com.awesomesoft.features.application.dto.ApplicationDtos.CreateApplicationRequest;
import com.awesomesoft.features.application.dto.ApplicationDtos.UpdateApplicationRequest;
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

@Tag(name = "Admin - Applications")
@SecurityRequirement(name = BASIC_AUTH)
@RestController
@RequestMapping("/features-api/v1/admin/applications")
@RequiredArgsConstructor
public class AdminApplicationController {

    private final ApplicationFacade applicationFacade;

    @Operation(summary = "List applications")
    @GetMapping
    public List<ApplicationResponse> list() {
        return applicationFacade.list();
    }

    @Operation(summary = "Create an application")
    @PostMapping
    public ResponseEntity<ApplicationResponse> create(@Valid @RequestBody CreateApplicationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(applicationFacade.create(request));
    }

    @Operation(summary = "Rename an application (slug is immutable)")
    @PatchMapping("/{slug}")
    public ApplicationResponse update(@PathVariable String slug,
                                      @Valid @RequestBody UpdateApplicationRequest request) {
        return applicationFacade.update(slug, request);
    }
}
