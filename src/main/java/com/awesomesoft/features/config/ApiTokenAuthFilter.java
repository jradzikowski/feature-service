package com.awesomesoft.features.config;

import com.awesomesoft.features.application.TokenFacade;
import com.awesomesoft.features.domain.ApiToken;
import com.awesomesoft.features.infrastructure.ApiTokenJpaRepository;
import com.awesomesoft.features.infrastructure.ApplicationJpaRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.List;
import java.util.Optional;

/**
 * Authenticates Evaluation API calls with a backend token: either the bootstrap token from
 * configuration or a panel-generated token looked up by SHA-256 in {@code api_tokens}. Lookups are
 * cached briefly so evaluations do not hit the database per request; revocation therefore takes up
 * to {@link #LOOKUP_TTL} to propagate.
 */
public class ApiTokenAuthFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Duration LOOKUP_TTL = Duration.ofSeconds(60);

    private final ApiTokenJpaRepository tokenRepository;
    private final ApplicationJpaRepository applicationRepository;
    private final byte[] bootstrapToken;
    private final String bootstrapApplicationSlug;

    private final Cache<String, Optional<ApplicationPrincipal>> lookupCache = Caffeine.newBuilder()
            .expireAfterWrite(LOOKUP_TTL)
            .maximumSize(1000)
            .build();

    public ApiTokenAuthFilter(ApiTokenJpaRepository tokenRepository,
                              ApplicationJpaRepository applicationRepository,
                              String bootstrapToken, String bootstrapApplicationSlug) {
        this.tokenRepository = tokenRepository;
        this.applicationRepository = applicationRepository;
        this.bootstrapToken = bootstrapToken == null ? new byte[0]
                : bootstrapToken.getBytes(StandardCharsets.UTF_8);
        this.bootstrapApplicationSlug = bootstrapApplicationSlug;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return request.getRequestURI().endsWith("/features-api/v1/health");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String presented = extractToken(request);
        Optional<ApplicationPrincipal> principal = presented == null ? Optional.empty() : resolve(presented);
        if (principal.isPresent()) {
            var authentication = new UsernamePasswordAuthenticationToken(principal.get(), null,
                    List.of(new SimpleGrantedAuthority("ROLE_EVALUATION")));
            SecurityContextHolder.getContext().setAuthentication(authentication);
            filterChain.doFilter(request, response);
            return;
        }
        SecurityContextHolder.clearContext();
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write("{\"status\":401,\"error\":\"Unauthorized\",\"message\":\"Invalid or missing token\"}");
    }

    private Optional<ApplicationPrincipal> resolve(String presented) {
        if (bootstrapToken.length > 0
                && MessageDigest.isEqual(bootstrapToken, presented.getBytes(StandardCharsets.UTF_8))) {
            return applicationRepository.findBySlug(bootstrapApplicationSlug)
                    .map(app -> new ApplicationPrincipal(app.getId(), app.getSlug()));
        }
        String hash = TokenFacade.sha256Hex(presented);
        return lookupCache.get(hash, h -> tokenRepository.findByTokenHashAndRevokedAtIsNull(h)
                .flatMap(this::toPrincipal));
    }

    private Optional<ApplicationPrincipal> toPrincipal(ApiToken token) {
        return applicationRepository.findById(token.getApplicationId())
                .map(app -> new ApplicationPrincipal(app.getId(), app.getSlug()));
    }

    private String extractToken(HttpServletRequest request) {
        String header = request.getHeader("Authorization");
        if (header != null && header.startsWith(BEARER_PREFIX)) {
            return header.substring(BEARER_PREFIX.length());
        }
        return null;
    }
}
