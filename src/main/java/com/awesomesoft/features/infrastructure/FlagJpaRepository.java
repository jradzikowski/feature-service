package com.awesomesoft.features.infrastructure;

import com.awesomesoft.features.domain.Flag;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlagJpaRepository extends JpaRepository<Flag, UUID> {

    List<Flag> findByApplicationIdOrderByFlagKey(UUID applicationId);

    List<Flag> findByApplicationIdAndArchivedFalseOrderByFlagKey(UUID applicationId);

    Optional<Flag> findByApplicationIdAndFlagKey(UUID applicationId, String flagKey);

    boolean existsByApplicationIdAndFlagKey(UUID applicationId, String flagKey);
}
