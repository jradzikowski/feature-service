package com.awesomesoft.features.rest;

import com.awesomesoft.features.application.dto.AdminDtos.LoginRequest;
import com.awesomesoft.features.application.dto.AdminDtos.SessionUserResponse;
import com.awesomesoft.features.config.LoginRateLimiter;
import com.awesomesoft.features.domain.AdminRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;

import static com.awesomesoft.features.config.OpenApiConfig.BASIC_AUTH;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Admin - Auth", description = "Panel session management")
@RestController
@RequestMapping("/features-api/v1/admin/auth")
@RequiredArgsConstructor
public class AdminAuthController {

    private final AuthenticationManager authenticationManager;
    private final SecurityContextRepository securityContextRepository;
    private final LoginRateLimiter loginRateLimiter;

    @Operation(summary = "Login (sets the FEATURES_SESSION cookie)")
    @PostMapping("/login")
    public ResponseEntity<SessionUserResponse> login(@Valid @RequestBody LoginRequest request,
                                                     HttpServletRequest httpRequest,
                                                     HttpServletResponse httpResponse) {
        loginRateLimiter.check(httpRequest.getRemoteAddr());
        Authentication authentication = authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.username(), request.password()));
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, httpRequest, httpResponse);
        return ResponseEntity.ok(toSessionUser(authentication));
    }

    @Operation(summary = "Current session user")
    @SecurityRequirement(name = BASIC_AUTH)
    @GetMapping("/me")
    public ResponseEntity<SessionUserResponse> me(Authentication authentication) {
        return ResponseEntity.ok(toSessionUser(authentication));
    }

    @Operation(summary = "Logout (invalidates the session)")
    @SecurityRequirement(name = BASIC_AUTH)
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest request, HttpServletResponse response) {
        HttpSession session = request.getSession(false);
        if (session != null) {
            session.invalidate();
        }
        SecurityContextHolder.clearContext();
        Cookie cookie = new Cookie("FEATURES_SESSION", null);
        cookie.setPath("/");
        cookie.setHttpOnly(true);
        cookie.setMaxAge(0);
        response.addCookie(cookie);
        return ResponseEntity.noContent().build();
    }

    private SessionUserResponse toSessionUser(Authentication authentication) {
        AdminRole role = authentication.getAuthorities().stream()
                .map(a -> a.getAuthority())
                .filter(a -> a.startsWith("ROLE_"))
                .map(a -> AdminRole.valueOf(a.substring("ROLE_".length())))
                .findFirst()
                .orElse(AdminRole.VIEWER);
        return new SessionUserResponse(authentication.getName(), role);
    }
}
