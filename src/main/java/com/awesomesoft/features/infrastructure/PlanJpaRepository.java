package com.awesomesoft.features.infrastructure;

import com.awesomesoft.features.domain.Plan;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanJpaRepository extends JpaRepository<Plan, UUID> {

    List<Plan> findByApplicationIdOrderByName(UUID applicationId);

    boolean existsByApplicationIdAndName(UUID applicationId, String name);

    Optional<Plan> findByIdAndApplicationId(UUID id, UUID applicationId);
}
