package com.awesomesoft.features.application;

import com.awesomesoft.features.application.dto.AdminDtos.CreateTokenRequest;
import com.awesomesoft.features.application.dto.AdminDtos.TokenCreatedResponse;
import com.awesomesoft.features.application.dto.AdminDtos.TokenResponse;
import com.awesomesoft.features.domain.ApiToken;
import com.awesomesoft.features.domain.AuditOperation;
import com.awesomesoft.features.domain.ClientApplication;
import com.awesomesoft.features.infrastructure.ApiTokenJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TokenFacade {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final ApplicationFacade applicationFacade;
    private final ApiTokenJpaRepository tokenRepository;
    private final AuditRecorder auditRecorder;

    @Transactional(readOnly = true)
    public List<TokenResponse> list(String slug) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        return tokenRepository.findByApplicationIdOrderByCreatedAtDesc(app.getId()).stream()
                .map(t -> new TokenResponse(t.getId(), t.getName(), t.getTokenPrefix(),
                        t.getCreatedAt(), t.getRevokedAt()))
                .toList();
    }

    /** The plaintext token is returned exactly once, here; only its SHA-256 is persisted. */
    @Transactional
    public TokenCreatedResponse create(String slug, CreateTokenRequest request) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        byte[] secret = new byte[32];
        RANDOM.nextBytes(secret);
        String token = "ffs_" + slug + "_" + Base64.getUrlEncoder().withoutPadding().encodeToString(secret);
        String prefix = token.substring(0, Math.min(token.length(), "ffs_".length() + slug.length() + 5));
        ApiToken saved = tokenRepository.save(new ApiToken(app.getId(), request.name(), sha256Hex(token), prefix));
        auditRecorder.record(app.getId(), request.name(), AuditOperation.TOKEN_CREATED, null, null, prefix);
        return new TokenCreatedResponse(saved.getId(), saved.getName(), saved.getTokenPrefix(),
                saved.getCreatedAt(), token);
    }

    @Transactional
    public void revoke(UUID tokenId) {
        ApiToken token = tokenRepository.findById(tokenId)
                .orElseThrow(() -> new NoSuchElementException("Token not found"));
        if (token.isRevoked()) {
            throw new IllegalStateException("Token is already revoked");
        }
        token.revoke();
        auditRecorder.record(token.getApplicationId(), token.getName(), AuditOperation.TOKEN_REVOKED, null,
                token.getTokenPrefix(), null);
    }

    public static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
