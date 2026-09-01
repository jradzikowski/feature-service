package com.awesomesoft.features.application;

import com.awesomesoft.features.application.dto.EvaluateResponse;
import com.awesomesoft.features.application.dto.EvaluateResponse.EvaluatedFlag;
import com.awesomesoft.features.application.dto.EvaluateResponse.Reason;
import com.awesomesoft.features.domain.ClientApplication;
import com.awesomesoft.features.domain.Flag;
import com.awesomesoft.features.domain.FlagOverride;
import com.awesomesoft.features.domain.ValueType;
import com.awesomesoft.features.infrastructure.ApplicationJpaRepository;
import com.awesomesoft.features.infrastructure.FlagJpaRepository;
import com.awesomesoft.features.infrastructure.FlagOverrideJpaRepository;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import tools.jackson.databind.JsonNode;

import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Resolves flag values for a context. The assembled per-application config is cached in Caffeine;
 * admin writes invalidate it after commit (the short TTL is only a safety net for missed
 * invalidations). Resolution order: locked > workgroup override > default.
 */
@Service
public class EvaluationService {

    private static final Duration SNAPSHOT_TTL = Duration.ofSeconds(30);

    private final ApplicationJpaRepository applicationRepository;
    private final FlagJpaRepository flagRepository;
    private final FlagOverrideJpaRepository overrideRepository;
    private final JsonValues jsonValues;
    private final MeterRegistry meterRegistry;

    private final Cache<UUID, Snapshot> snapshots = Caffeine.newBuilder()
            .expireAfterWrite(SNAPSHOT_TTL)
            .maximumSize(200)
            .build();

    public EvaluationService(ApplicationJpaRepository applicationRepository, FlagJpaRepository flagRepository,
                             FlagOverrideJpaRepository overrideRepository, JsonValues jsonValues,
                             MeterRegistry meterRegistry) {
        this.applicationRepository = applicationRepository;
        this.flagRepository = flagRepository;
        this.overrideRepository = overrideRepository;
        this.jsonValues = jsonValues;
        this.meterRegistry = meterRegistry;
    }

    public EvaluateResponse evaluate(UUID applicationId, String applicationSlug, UUID workgroupId) {
        Snapshot snapshot = snapshots.get(applicationId, this::loadSnapshot);
        Map<String, EvaluatedFlag> flags = new LinkedHashMap<>();
        for (FlagConfig flag : snapshot.flags()) {
            flags.put(flag.key(), resolve(flag, workgroupId));
        }
        meterRegistry.counter("features.evaluations", "application", applicationSlug).increment();
        return new EvaluateResponse(applicationSlug, snapshot.version(), Instant.now(), flags);
    }

    /**
     * Invalidates the snapshot once the surrounding transaction commits, so a concurrent read cannot
     * re-cache pre-commit state. Falls back to immediate invalidation outside a transaction.
     */
    public void invalidateAfterCommit(UUID applicationId) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    snapshots.invalidate(applicationId);
                }
            });
        } else {
            snapshots.invalidate(applicationId);
        }
    }

    private EvaluatedFlag resolve(FlagConfig flag, UUID workgroupId) {
        if (flag.locked()) {
            return new EvaluatedFlag(flag.type(), flag.defaultValue(), Reason.LOCKED);
        }
        if (workgroupId != null) {
            JsonNode override = flag.overrides().get(workgroupId);
            if (override != null) {
                return new EvaluatedFlag(flag.type(), override, Reason.WORKGROUP_OVERRIDE);
            }
        }
        return new EvaluatedFlag(flag.type(), flag.defaultValue(), Reason.DEFAULT);
    }

    private Snapshot loadSnapshot(UUID applicationId) {
        long version = applicationRepository.findById(applicationId)
                .map(ClientApplication::getConfigVersion)
                .orElse(0L);
        List<Flag> flags = flagRepository.findByApplicationIdAndArchivedFalseOrderByFlagKey(applicationId);
        Map<UUID, Map<UUID, JsonNode>> overridesByFlag = new HashMap<>();
        for (FlagOverride override : overrideRepository.findAllByApplicationId(applicationId)) {
            overridesByFlag.computeIfAbsent(override.getFlagId(), k -> new HashMap<>())
                    .put(override.getWorkgroupId(), jsonValues.parse(override.getValue()));
        }
        List<FlagConfig> configs = flags.stream()
                .map(f -> new FlagConfig(f.getFlagKey(), f.getValueType(), jsonValues.parse(f.getDefaultValue()),
                        f.isLocked(), overridesByFlag.getOrDefault(f.getId(), Map.of())))
                .toList();
        return new Snapshot(version, configs);
    }

    private record FlagConfig(String key, ValueType type, JsonNode defaultValue, boolean locked,
                              Map<UUID, JsonNode> overrides) {
    }

    private record Snapshot(long version, List<FlagConfig> flags) {
    }
}
