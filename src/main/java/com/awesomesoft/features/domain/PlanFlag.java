package com.awesomesoft.features.domain;

import jakarta.persistence.*;
import lombok.*;
import java.util.UUID;

@Entity
@Table(name = "plan_flags")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PlanFlag {

    @Id
    private UUID id;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "flag_id", nullable = false)
    private UUID flagId;

    @Column(nullable = false)
    private String value;

    public PlanFlag(UUID planId, UUID flagId, String value) {
        this.id = UUID.randomUUID();
        this.planId = planId;
        this.flagId = flagId;
        this.value = value;
    }

    public void setValue(String value) {
        this.value = value;
    }
}
