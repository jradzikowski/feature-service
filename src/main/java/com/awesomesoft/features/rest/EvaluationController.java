package com.awesomesoft.features.rest;

import com.awesomesoft.features.application.EvaluationService;
import com.awesomesoft.features.application.dto.EvaluateRequest;
import com.awesomesoft.features.application.dto.EvaluateResponse;
import com.awesomesoft.features.config.ApplicationPrincipal;
import io.micrometer.core.instrument.MeterRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import static com.awesomesoft.features.config.OpenApiConfig.BEARER_AUTH;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@Tag(name = "Evaluation", description = "Evaluated flag values for a context; backend-token auth")
@SecurityRequirement(name = BEARER_AUTH)
@RestController
@RequestMapping("/features-api/v1")
@RequiredArgsConstructor
public class EvaluationController {

    private final EvaluationService evaluationService;
    private final MeterRegistry meterRegistry;

    @Operation(summary = "Evaluate all active flags of the token's application for the given context",
            description = "Supports ETag/If-None-Match: returns 304 when the configuration has not changed.")
    @PostMapping("/evaluate")
    public ResponseEntity<EvaluateResponse> evaluate(
            @Valid @RequestBody EvaluateRequest request,
            @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false) String ifNoneMatch,
            Authentication authentication) {
        ApplicationPrincipal principal = (ApplicationPrincipal) authentication.getPrincipal();
        EvaluateResponse response = evaluationService.evaluate(principal.applicationId(), principal.slug(),
                request.context().workgroupId());
        String etag = "\"" + response.configVersion() + "\"";
        if (etag.equals(ifNoneMatch)) {
            meterRegistry.counter("features.evaluations.not_modified", "application", principal.slug())
                    .increment();
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag(etag).build();
        }
        return ResponseEntity.ok().eTag(etag).body(response);
    }

    @Operation(summary = "Health check (no authentication)")
    @GetMapping("/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
