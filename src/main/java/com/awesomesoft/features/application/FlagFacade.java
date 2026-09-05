package com.awesomesoft.features.application;

import com.awesomesoft.features.application.dto.FlagDtos.CreateFlagRequest;
import com.awesomesoft.features.application.dto.FlagDtos.FlagDetailResponse;
import com.awesomesoft.features.application.dto.FlagDtos.FlagResponse;
import com.awesomesoft.features.application.dto.FlagDtos.OverrideResponse;
import com.awesomesoft.features.application.dto.FlagDtos.RegisterFlagsRequest;
import com.awesomesoft.features.application.dto.FlagDtos.RegistrationResponse;
import com.awesomesoft.features.application.dto.FlagDtos.SetOverrideRequest;
import com.awesomesoft.features.application.dto.FlagDtos.TypeMismatch;
import com.awesomesoft.features.application.dto.FlagDtos.UpdateFlagRequest;
import com.awesomesoft.features.application.dto.FlagDtos.WorkgroupOverrideResponse;
import com.awesomesoft.features.domain.AuditOperation;
import com.awesomesoft.features.domain.ClientApplication;
import com.awesomesoft.features.domain.Flag;
import com.awesomesoft.features.domain.FlagOverride;
import com.awesomesoft.features.infrastructure.FlagJpaRepository;
import com.awesomesoft.features.infrastructure.FlagOverrideJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class FlagFacade {

    /** RELEASE/EXPERIMENT flags older than this land in the stale report even without expires_at. */
    private static final int RELEASE_BUDGET_DAYS = 40;

    private final ApplicationFacade applicationFacade;
    private final FlagJpaRepository flagRepository;
    private final FlagOverrideJpaRepository overrideRepository;
    private final JsonValues jsonValues;
    private final AuditRecorder auditRecorder;
    private final EvaluationService evaluationService;

    @Transactional(readOnly = true)
    public List<FlagResponse> list(String slug, boolean includeArchived) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        List<Flag> flags = includeArchived
                ? flagRepository.findByApplicationIdOrderByFlagKey(app.getId())
                : flagRepository.findByApplicationIdAndArchivedFalseOrderByFlagKey(app.getId());
        Map<UUID, Long> overrideCounts = overrideRepository.findAllByApplicationId(app.getId()).stream()
                .collect(Collectors.groupingBy(FlagOverride::getFlagId, Collectors.counting()));
        return flags.stream()
                .map(f -> toResponse(f, overrideCounts.getOrDefault(f.getId(), 0L)))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FlagResponse> listStale(String slug) {
        LocalDate today = LocalDate.now();
        return list(slug, false).stream()
                .filter(response -> isStaleResponse(response, today))
                .toList();
    }

    @Transactional
    public FlagDetailResponse create(String slug, CreateFlagRequest request) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        if (flagRepository.existsByApplicationIdAndFlagKey(app.getId(), request.flagKey())) {
            throw new IllegalStateException("Flag '" + request.flagKey() + "' already exists in '" + slug + "'");
        }
        Flag flag = createFlag(app, request);
        configChanged(app);
        return toDetailResponse(flag);
    }

    /**
     * Self-registration of the catalog an application declares in its code (called by the consumer
     * itself with its backend token, not from the panel): creates the flags this application does
     * not have yet and reports what it found.
     *
     * <p>Create-only by design. Name, description, default value, kind and overrides of an existing
     * flag belong to whoever operates the panel and are never overwritten by a deploy; an archived
     * flag counts as existing and stays archived. A declared type that disagrees with the stored
     * one is reported rather than applied — {@code value_type} is immutable.
     */
    @Transactional
    public RegistrationResponse register(String slug, RegisterFlagsRequest request) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        Map<String, Flag> known = new HashMap<>();
        flagRepository.findByApplicationIdOrderByFlagKey(app.getId())
                .forEach(flag -> known.put(flag.getFlagKey(), flag));

        List<String> created = new ArrayList<>();
        List<String> existing = new ArrayList<>();
        List<TypeMismatch> mismatched = new ArrayList<>();
        for (CreateFlagRequest declaration : request.flags()) {
            Flag flag = known.get(declaration.flagKey());
            if (flag != null) {
                existing.add(declaration.flagKey());
                if (flag.getValueType() != declaration.valueType()) {
                    mismatched.add(new TypeMismatch(declaration.flagKey(), declaration.valueType(),
                            flag.getValueType()));
                }
                continue;
            }
            Flag saved = createFlag(app, declaration);
            // Guards against a key repeated inside one payload hitting the unique constraint.
            known.put(saved.getFlagKey(), saved);
            created.add(saved.getFlagKey());
        }
        if (!created.isEmpty()) {
            configChanged(app);
        }
        return new RegistrationResponse(app.getSlug(), created, existing, mismatched);
    }

    private Flag createFlag(ClientApplication app, CreateFlagRequest request) {
        String defaultValue = jsonValues.validateAndSerialize(request.valueType(), request.defaultValue());
        Flag flag = flagRepository.save(new Flag(app.getId(), request.flagKey(), request.name(),
                request.description(), request.valueType(), defaultValue, request.flagKind(),
                request.expiresAt(), request.owner()));
        auditRecorder.record(app.getId(), flag.getFlagKey(), AuditOperation.FLAG_CREATED, null, null, defaultValue);
        return flag;
    }

    @Transactional(readOnly = true)
    public FlagDetailResponse get(String slug, String flagKey) {
        return toDetailResponse(getFlag(slug, flagKey));
    }

    @Transactional
    public FlagDetailResponse update(String slug, String flagKey, UpdateFlagRequest request) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        Flag flag = getFlag(app, flagKey);

        if (request.name() != null) {
            flag.setName(request.name());
        }
        if (request.description() != null) {
            flag.setDescription(request.description());
        }
        if (Boolean.TRUE.equals(request.clearExpiresAt())) {
            flag.setExpiresAt(null);
        } else if (request.expiresAt() != null) {
            flag.setExpiresAt(request.expiresAt());
        }
        if (request.owner() != null) {
            flag.setOwner(request.owner());
        }
        if (request.defaultValue() != null) {
            String newValue = jsonValues.validateAndSerialize(flag.getValueType(), request.defaultValue());
            if (!Objects.equals(flag.getDefaultValue(), newValue)) {
                auditRecorder.record(app.getId(), flagKey, AuditOperation.FLAG_UPDATED, null,
                        flag.getDefaultValue(), newValue);
                flag.setDefaultValue(newValue);
            }
        }
        if (request.locked() != null && request.locked() != flag.isLocked()) {
            flag.setLocked(request.locked());
            auditRecorder.record(app.getId(), flagKey,
                    request.locked() ? AuditOperation.FLAG_LOCKED : AuditOperation.FLAG_UNLOCKED, null, null, null);
        }
        if (request.archived() != null && request.archived() != flag.isArchived()) {
            flag.setArchived(request.archived());
            auditRecorder.record(app.getId(), flagKey, AuditOperation.FLAG_ARCHIVED, null, null,
                    String.valueOf(request.archived()));
        }
        flag.touch();
        configChanged(app);
        return toDetailResponse(flag);
    }

    @Transactional
    public void delete(String slug, String flagKey) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        Flag flag = getFlag(app, flagKey);
        if (!flag.isArchived()) {
            throw new IllegalStateException("Only archived flags can be deleted; archive '" + flagKey + "' first");
        }
        // Override rows go away via ON DELETE CASCADE.
        flagRepository.delete(flag);
        auditRecorder.record(app.getId(), flagKey, AuditOperation.FLAG_DELETED, null, null, null);
        configChanged(app);
    }

    @Transactional
    public OverrideResponse setOverride(String slug, String flagKey, UUID workgroupId, SetOverrideRequest request) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        Flag flag = getFlag(app, flagKey);
        String value = jsonValues.validateAndSerialize(flag.getValueType(), request.value());
        FlagOverride override = overrideRepository.findByFlagIdAndWorkgroupId(flag.getId(), workgroupId)
                .orElse(null);
        String oldValue = override != null ? override.getValue() : null;
        if (override == null) {
            override = overrideRepository.save(new FlagOverride(flag.getId(), workgroupId, value, request.note()));
        } else {
            override.update(value, request.note());
        }
        auditRecorder.record(app.getId(), flagKey, AuditOperation.OVERRIDE_SET, workgroupId, oldValue, value);
        configChanged(app);
        return toOverrideResponse(override);
    }

    @Transactional
    public void removeOverride(String slug, String flagKey, UUID workgroupId) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        Flag flag = getFlag(app, flagKey);
        FlagOverride override = overrideRepository.findByFlagIdAndWorkgroupId(flag.getId(), workgroupId)
                .orElseThrow(() -> new NoSuchElementException(
                        "No override for workgroup " + workgroupId + " on flag '" + flagKey + "'"));
        overrideRepository.delete(override);
        auditRecorder.record(app.getId(), flagKey, AuditOperation.OVERRIDE_REMOVED, workgroupId,
                override.getValue(), null);
        configChanged(app);
    }

    @Transactional(readOnly = true)
    public List<WorkgroupOverrideResponse> listWorkgroupOverrides(String slug, UUID workgroupId) {
        ClientApplication app = applicationFacade.getBySlug(slug);
        Map<UUID, String> flagKeysById = flagRepository.findByApplicationIdOrderByFlagKey(app.getId()).stream()
                .collect(Collectors.toMap(Flag::getId, Flag::getFlagKey));
        return overrideRepository.findByApplicationIdAndWorkgroupId(app.getId(), workgroupId).stream()
                .map(o -> new WorkgroupOverrideResponse(flagKeysById.get(o.getFlagId()),
                        jsonValues.parse(o.getValue()), o.getNote(), o.getUpdatedAt()))
                .sorted((a, b) -> a.flagKey().compareTo(b.flagKey()))
                .toList();
    }

    private void configChanged(ClientApplication app) {
        app.bumpConfigVersion();
        evaluationService.invalidateAfterCommit(app.getId());
    }

    private Flag getFlag(String slug, String flagKey) {
        return getFlag(applicationFacade.getBySlug(slug), flagKey);
    }

    private Flag getFlag(ClientApplication app, String flagKey) {
        return flagRepository.findByApplicationIdAndFlagKey(app.getId(), flagKey)
                .orElseThrow(() -> new NoSuchElementException(
                        "Flag '" + flagKey + "' not found in '" + app.getSlug() + "'"));
    }

    private boolean isStaleResponse(FlagResponse response, LocalDate today) {
        if (response.expiresAt() != null && response.expiresAt().isBefore(today)) {
            return true;
        }
        return switch (response.flagKind()) {
            case RELEASE, EXPERIMENT -> response.createdAt().toLocalDate().plusDays(RELEASE_BUDGET_DAYS)
                    .isBefore(today);
            default -> false;
        };
    }

    private FlagResponse toResponse(Flag flag, long overrideCount) {
        return new FlagResponse(flag.getFlagKey(), flag.getName(), flag.getDescription(), flag.getValueType(),
                jsonValues.parse(flag.getDefaultValue()), flag.getFlagKind(), flag.isLocked(), flag.isArchived(),
                flag.getExpiresAt(), flag.getOwner(), overrideCount, flag.getCreatedAt(), flag.getUpdatedAt());
    }

    private FlagDetailResponse toDetailResponse(Flag flag) {
        List<OverrideResponse> overrides = overrideRepository.findByFlagId(flag.getId()).stream()
                .map(this::toOverrideResponse)
                .toList();
        return new FlagDetailResponse(flag.getFlagKey(), flag.getName(), flag.getDescription(), flag.getValueType(),
                jsonValues.parse(flag.getDefaultValue()), flag.getFlagKind(), flag.isLocked(), flag.isArchived(),
                flag.getExpiresAt(), flag.getOwner(), overrides.size(), flag.getCreatedAt(), flag.getUpdatedAt(),
                overrides);
    }

    private OverrideResponse toOverrideResponse(FlagOverride override) {
        return new OverrideResponse(override.getWorkgroupId(), jsonValues.parse(override.getValue()),
                override.getNote(), override.getUpdatedAt());
    }
}
