package com.awesomesoft.features.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "workgroups")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Workgroup {

    @Id
    private UUID id;

    @Column(nullable = false, length = 255)
    private String name;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Workgroup(UUID id, String name) {
        this.id = id;
        this.name = name;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    public void rename(String name) {
        this.name = name;
        this.updatedAt = LocalDateTime.now();
    }
}
