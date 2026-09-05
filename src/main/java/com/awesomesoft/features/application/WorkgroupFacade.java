package com.awesomesoft.features.application;

import com.awesomesoft.features.application.dto.WorkgroupDtos.CreateWorkgroupRequest;
import com.awesomesoft.features.application.dto.WorkgroupDtos.UpdateWorkgroupRequest;
import com.awesomesoft.features.application.dto.WorkgroupDtos.WorkgroupResponse;
import com.awesomesoft.features.domain.Workgroup;
import com.awesomesoft.features.infrastructure.WorkgroupJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class WorkgroupFacade {

    private final WorkgroupJpaRepository workgroupRepository;

    @Transactional(readOnly = true)
    public List<WorkgroupResponse> list(String nameQuery) {
        List<Workgroup> workgroups = (nameQuery != null && !nameQuery.isBlank())
                ? workgroupRepository.searchByName(nameQuery)
                : workgroupRepository.findAllByOrderByNameAsc();
        return workgroups.stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public WorkgroupResponse get(UUID id) {
        return toResponse(getById(id));
    }

    @Transactional
    public WorkgroupResponse create(CreateWorkgroupRequest request) {
        if (workgroupRepository.existsById(request.id())) {
            throw new IllegalStateException("Workgroup with id '" + request.id() + "' already exists");
        }
        return toResponse(workgroupRepository.save(new Workgroup(request.id(), request.name())));
    }

    @Transactional
    public WorkgroupResponse update(UUID id, UpdateWorkgroupRequest request) {
        Workgroup workgroup = getById(id);
        workgroup.rename(request.name());
        return toResponse(workgroup);
    }

    @Transactional
    public void delete(UUID id) {
        Workgroup workgroup = getById(id);
        workgroupRepository.delete(workgroup);
    }

    public Workgroup getById(UUID id) {
        return workgroupRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("Workgroup '" + id + "' not found"));
    }

    private WorkgroupResponse toResponse(Workgroup w) {
        return new WorkgroupResponse(w.getId(), w.getName(), w.getCreatedAt(), w.getUpdatedAt());
    }
}
