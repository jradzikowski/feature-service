package com.awesomesoft.features.application;

import com.awesomesoft.features.domain.AuditOperation;
import com.awesomesoft.features.domain.FlagAuditEntry;
import com.awesomesoft.features.infrastructure.FlagAuditJpaRepository;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.UUID;

/** Appends journal rows for every admin mutation; called inside the mutating transaction. */
@Component
@RequiredArgsConstructor
public class AuditRecorder {

    private final FlagAuditJpaRepository auditRepository;
    private final MeterRegistry meterRegistry;

    public void record(UUID applicationId, String flagKey, AuditOperation operation, UUID workgroupId,
                       String oldValue, String newValue) {
        auditRepository.save(new FlagAuditEntry(applicationId, flagKey, operation, workgroupId,
                oldValue, newValue, currentUsername()));
        meterRegistry.counter("features.admin.changes", "operation", operation.name()).increment();
    }

    private String currentUsername() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "system";
    }
}
