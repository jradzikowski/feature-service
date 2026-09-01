package com.awesomesoft.features.infrastructure;

import com.awesomesoft.features.domain.ApiToken;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ApiTokenJpaRepository extends JpaRepository<ApiToken, UUID> {

    Optional<ApiToken> findByTokenHashAndRevokedAtIsNull(String tokenHash);

    List<ApiToken> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId);
}
