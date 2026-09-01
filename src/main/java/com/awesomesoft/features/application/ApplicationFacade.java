package com.awesomesoft.features.application;

import com.awesomesoft.features.application.dto.ApplicationDtos.ApplicationResponse;
import com.awesomesoft.features.application.dto.ApplicationDtos.CreateApplicationRequest;
import com.awesomesoft.features.application.dto.ApplicationDtos.UpdateApplicationRequest;
import com.awesomesoft.features.domain.ClientApplication;
import com.awesomesoft.features.infrastructure.ApplicationJpaRepository;
import com.awesomesoft.features.infrastructure.FlagJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;

@Service
@RequiredArgsConstructor
public class ApplicationFacade {

    private final ApplicationJpaRepository applicationRepository;
    private final FlagJpaRepository flagRepository;

    @Transactional(readOnly = true)
    public List<ApplicationResponse> list() {
        return applicationRepository.findAll().stream()
                .map(app -> toResponse(app, flagRepository.findByApplicationIdOrderByFlagKey(app.getId()).size()))
                .toList();
    }

    @Transactional
    public ApplicationResponse create(CreateApplicationRequest request) {
        if (applicationRepository.existsBySlug(request.slug())) {
            throw new IllegalStateException("Application with slug '" + request.slug() + "' already exists");
        }
        ClientApplication app = applicationRepository.save(new ClientApplication(request.slug(), request.name()));
        return toResponse(app, 0);
    }

    @Transactional
    public ApplicationResponse update(String slug, UpdateApplicationRequest request) {
        ClientApplication app = getBySlug(slug);
        app.setName(request.name());
        app.setUpdatedAt(LocalDateTime.now());
        return toResponse(app, flagRepository.findByApplicationIdOrderByFlagKey(app.getId()).size());
    }

    @Transactional(readOnly = true)
    public ClientApplication getBySlug(String slug) {
        return applicationRepository.findBySlug(slug)
                .orElseThrow(() -> new NoSuchElementException("Application '" + slug + "' not found"));
    }

    private ApplicationResponse toResponse(ClientApplication app, long flagCount) {
        return new ApplicationResponse(app.getId(), app.getSlug(), app.getName(), app.getConfigVersion(),
                flagCount, app.getCreatedAt());
    }
}
