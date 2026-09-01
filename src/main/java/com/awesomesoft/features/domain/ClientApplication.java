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

/** An application consuming flags (e.g. audit). Boundary for flags and API tokens. */
@Entity
@Table(name = "applications")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ClientApplication {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 64)
    private String slug;

    @Column(nullable = false)
    private String name;

    /** Monotonic change counter; source of the Evaluation API ETag. */
    @Column(name = "config_version", nullable = false)
    private long configVersion;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public ClientApplication(String slug, String name) {
        this.id = UUID.randomUUID();
        this.slug = slug;
        this.name = name;
        this.configVersion = 0;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void bumpConfigVersion() {
        this.configVersion++;
        this.updatedAt = LocalDateTime.now();
    }
}
