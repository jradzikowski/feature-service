package com.awesomesoft.features.domain;

import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "workgroup_plans")
@Getter @Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class WorkgroupPlan {

    @Id
    private UUID id;

    @Column(name = "workgroup_id", nullable = false)
    private UUID workgroupId;

    @Column(name = "plan_id", nullable = false)
    private UUID planId;

    @Column(name = "application_id", nullable = false)
    private UUID applicationId;

    @Column(name = "assigned_at", nullable = false)
    private LocalDateTime assignedAt;

    public WorkgroupPlan(UUID workgroupId, UUID planId, UUID applicationId) {
        this.id = UUID.randomUUID();
        this.workgroupId = workgroupId;
        this.planId = planId;
        this.applicationId = applicationId;
        this.assignedAt = LocalDateTime.now();
    }

    public void reassign(UUID planId) {
        this.planId = planId;
        this.assignedAt = LocalDateTime.now();
    }
}
