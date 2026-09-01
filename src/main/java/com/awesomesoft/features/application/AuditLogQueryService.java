package com.awesomesoft.features.application;

import com.awesomesoft.features.application.dto.AdminDtos.AuditEntryResponse;
import com.awesomesoft.features.domain.ClientApplication;
import com.awesomesoft.features.domain.FlagAuditEntry;
import com.awesomesoft.features.infrastructure.FlagAuditJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.JsonNode;

@Service
@RequiredArgsConstructor
public class AuditLogQueryService {

    private final ApplicationFacade applicationFacade;
    private final FlagAuditJpaRepository auditRepository;
    private final JsonValues jsonValues;

    @Transactional(readOnly = true)
    public Page<AuditEntryResponse> find(String slug, String flagKey, Pageable pageable) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        Page<FlagAuditEntry> page = (flagKey == null || flagKey.isBlank())
                ? auditRepository.findByApplicationIdOrderByCreatedAtDesc(app.getId(), pageable)
                : auditRepository.findByApplicationIdAndFlagKeyOrderByCreatedAtDesc(app.getId(), flagKey, pageable);
        return page.map(this::toResponse);
    }

    private AuditEntryResponse toResponse(FlagAuditEntry entry) {
        return new AuditEntryResponse(entry.getFlagKey(), entry.getOperation(), entry.getWorkgroupId(),
                parseLenient(entry.getOldValue()), parseLenient(entry.getNewValue()),
                entry.getActorUsername(), entry.getCreatedAt());
    }

    /** Audit values are usually JSON, but token/archival entries store plain text — keep those readable. */
    private JsonNode parseLenient(String value) {
        if (value == null) {
            return null;
        }
        try {
            return jsonValues.parse(value);
        } catch (RuntimeException e) {
            return tools.jackson.databind.node.StringNode.valueOf(value);
        }
    }
}
