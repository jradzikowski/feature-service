package com.awesomesoft.features.infrastructure;

import com.awesomesoft.features.domain.FlagOverride;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FlagOverrideJpaRepository extends JpaRepository<FlagOverride, UUID> {

    List<FlagOverride> findByFlagId(UUID flagId);

    Optional<FlagOverride> findByFlagIdAndWorkgroupId(UUID flagId, UUID workgroupId);

    long countByFlagId(UUID flagId);

    @Query("select o from FlagOverride o where o.flagId in "
            + "(select f.id from Flag f where f.applicationId = :applicationId)")
    List<FlagOverride> findAllByApplicationId(@Param("applicationId") UUID applicationId);

    @Query("select o from FlagOverride o where o.workgroupId = :workgroupId and o.flagId in "
            + "(select f.id from Flag f where f.applicationId = :applicationId)")
    List<FlagOverride> findByApplicationIdAndWorkgroupId(@Param("applicationId") UUID applicationId,
                                                         @Param("workgroupId") UUID workgroupId);
}
