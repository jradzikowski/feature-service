package com.awesomesoft.features.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

/** Append-only change journal row; never updated or deleted. */
@Entity
@Table(name = "flag_audit_log")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlagAuditEntry {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "flag_key", nullable = false, length = 128)
    private String flagKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private AuditOperation operation;

    @Column(name = "workgroup_id")
    private UUID workgroupId;

    @Column(name = "old_value")
    private String oldValue;

    @Column(name = "new_value")
    private String newValue;

    @Column(name = "actor_username", nullable = false)
    private String actorUsername;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public FlagAuditEntry(UUID applicationId, String flagKey, AuditOperation operation, UUID workgroupId,
                          String oldValue, String newValue, String actorUsername) {
        this.id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.flagKey = flagKey;
        this.operation = operation;
        this.workgroupId = workgroupId;
        this.oldValue = oldValue;
        this.newValue = newValue;
        this.actorUsername = actorUsername;
        this.createdAt = LocalDateTime.now();
    }
}
