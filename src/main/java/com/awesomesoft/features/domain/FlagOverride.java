package com.awesomesoft.features.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.UUID;

/** Per-workgroup value; wins over the flag's default unless the flag is locked. */
@Entity
@Table(name = "flag_overrides")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FlagOverride {

    @Id
    private UUID id;

    @Column(name = "flag_id", nullable = false)
    private UUID flagId;

    @Column(name = "workgroup_id", nullable = false)
    private UUID workgroupId;

    /** JSON text conforming to the flag's value type. */
    @Column(nullable = false)
    private String value;

    @Column(length = 512)
    private String note;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public FlagOverride(UUID flagId, UUID workgroupId, String value, String note) {
        this.id = UUID.randomUUID();
        this.flagId = flagId;
        this.workgroupId = workgroupId;
        this.value = value;
        this.note = note;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void update(String value, String note) {
        this.value = value;
        this.note = note;
        this.updatedAt = LocalDateTime.now();
    }
}
