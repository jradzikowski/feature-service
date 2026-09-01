package com.awesomesoft.features.infrastructure;

import com.awesomesoft.features.domain.ClientApplication;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ApplicationJpaRepository extends JpaRepository<ClientApplication, UUID> {

    Optional<ClientApplication> findBySlug(String slug);

    boolean existsBySlug(String slug);
}
