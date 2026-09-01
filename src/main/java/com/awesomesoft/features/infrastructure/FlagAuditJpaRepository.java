package com.awesomesoft.features.infrastructure;

import com.awesomesoft.features.domain.FlagAuditEntry;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface FlagAuditJpaRepository extends JpaRepository<FlagAuditEntry, UUID> {

    Page<FlagAuditEntry> findByApplicationIdOrderByCreatedAtDesc(UUID applicationId, Pageable pageable);

    Page<FlagAuditEntry> findByApplicationIdAndFlagKeyOrderByCreatedAtDesc(UUID applicationId, String flagKey,
                                                                           Pageable pageable);
}
