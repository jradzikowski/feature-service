package com.awesomesoft.features.infrastructure;

import com.awesomesoft.features.domain.PlanFlag;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PlanFlagJpaRepository extends JpaRepository<PlanFlag, UUID> {

    List<PlanFlag> findByPlanId(UUID planId);

    Optional<PlanFlag> findByPlanIdAndFlagId(UUID planId, UUID flagId);

    @Query("select pf from PlanFlag pf where pf.planId in (select p.id from Plan p where p.applicationId = :applicationId)")
    List<PlanFlag> findAllByApplicationId(@Param("applicationId") UUID applicationId);
}
