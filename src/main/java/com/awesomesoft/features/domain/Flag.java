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
import lombok.Setter;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Flag definition. {@code flagKey} and {@code valueType} are immutable after creation;
 * {@code defaultValue} is the global value (editing it is the global switch).
 */
@Entity
@Table(name = "flags")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Flag {

    @Id
    private UUID id;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "flag_key", nullable = false, length = 128)
    private String flagKey;

    @Column(nullable = false)
    private String name;

    @Column
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", nullable = false, length = 16)
    private ValueType valueType;

    /** JSON text conforming to {@link #valueType}. */
    @Column(name = "default_value", nullable = false)
    private String defaultValue;

    @Enumerated(EnumType.STRING)
    @Column(name = "flag_kind", nullable = false, length = 16)
    private FlagKind flagKind;

    /** Kill switch: when true, overrides are ignored and everyone gets the default. */
    @Column(nullable = false)
    private boolean locked;

    @Column(nullable = false)
    private boolean archived;

    @Column(name = "expires_at")
    private LocalDate expiresAt;

    @Column
    private String owner;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Flag(UUID applicationId, String flagKey, String name, String description, ValueType valueType,
                String defaultValue, FlagKind flagKind, LocalDate expiresAt, String owner) {
        this.id = UUID.randomUUID();
        this.applicationId = applicationId;
        this.flagKey = flagKey;
        this.name = name;
        this.description = description;
        this.valueType = valueType;
        this.defaultValue = defaultValue;
        this.flagKind = flagKind;
        this.expiresAt = expiresAt;
        this.owner = owner;
        this.locked = false;
        this.archived = false;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void touch() {
        this.updatedAt = LocalDateTime.now();
    }

    public boolean isStale(LocalDate today, int releaseBudgetDays) {
        if (archived) {
            return false;
        }
        if (expiresAt != null && expiresAt.isBefore(today)) {
            return true;
        }
        return (flagKind == FlagKind.RELEASE || flagKind == FlagKind.EXPERIMENT)
                && createdAt.toLocalDate().plusDays(releaseBudgetDays).isBefore(today);
    }
}
