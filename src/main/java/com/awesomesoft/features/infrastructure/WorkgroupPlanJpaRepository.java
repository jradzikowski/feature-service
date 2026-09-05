package com.awesomesoft.features.infrastructure;

import com.awesomesoft.features.domain.WorkgroupPlan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkgroupPlanJpaRepository extends JpaRepository<WorkgroupPlan, UUID> {

    Optional<WorkgroupPlan> findByWorkgroupIdAndApplicationId(UUID workgroupId, UUID applicationId);

    List<WorkgroupPlan> findByApplicationId(UUID applicationId);
}
