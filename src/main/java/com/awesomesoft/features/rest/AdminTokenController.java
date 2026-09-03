package com.awesomesoft.features.rest;

import com.awesomesoft.features.application.TokenFacade;
import com.awesomesoft.features.application.dto.AdminDtos.CreateTokenRequest;
import com.awesomesoft.features.application.dto.AdminDtos.TokenCreatedResponse;
import com.awesomesoft.features.application.dto.AdminDtos.TokenResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import static com.awesomesoft.features.config.OpenApiConfig.BASIC_AUTH;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@Tag(name = "Admin - Tokens")
@SecurityRequirement(name = BASIC_AUTH)
@RestController
@RequestMapping("/features-api/v1/admin")
@RequiredArgsConstructor
public class AdminTokenController {

    private final TokenFacade tokenFacade;

    @Operation(summary = "List backend tokens of an application (no secrets)")
    @GetMapping("/applications/{slug}/tokens")
    public List<TokenResponse> list(@PathVariable String slug) {
        return tokenFacade.list(slug);
    }

    @Operation(summary = "Generate a token — the plaintext is visible only in this response")
    @PostMapping("/applications/{slug}/tokens")
    public ResponseEntity<TokenCreatedResponse> create(@PathVariable String slug,
                                                       @Valid @RequestBody CreateTokenRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(tokenFacade.create(slug, request));
    }

    @Operation(summary = "Revoke a token")
    @PostMapping("/tokens/{id}/revoke")
    public ResponseEntity<Void> revoke(@PathVariable UUID id) {
        tokenFacade.revoke(id);
        return ResponseEntity.noContent().build();
    }
}
