package com.awesomesoft.features.application;

import com.awesomesoft.features.application.dto.AdminDtos.AdminUserResponse;
import com.awesomesoft.features.application.dto.AdminDtos.CreateAdminUserRequest;
import com.awesomesoft.features.application.dto.AdminDtos.UpdateAdminUserRequest;
import com.awesomesoft.features.domain.AdminRole;
import com.awesomesoft.features.domain.AdminUser;
import com.awesomesoft.features.infrastructure.AdminUserJpaRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminUserFacade {

    private final AdminUserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Transactional(readOnly = true)
    public List<AdminUserResponse> list() {
        return userRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AdminUserResponse create(CreateAdminUserRequest request) {
        if (userRepository.existsByUsername(request.username())) {
            throw new IllegalStateException("User '" + request.username() + "' already exists");
        }
        AdminUser user = userRepository.save(new AdminUser(request.username(),
                passwordEncoder.encode(request.password()), request.role()));
        return toResponse(user);
    }

    @Transactional
    public AdminUserResponse update(UUID id, UpdateAdminUserRequest request) {
        AdminUser user = userRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("User not found"));
        boolean self = user.getUsername().equals(currentUsername());
        if (self && (Boolean.FALSE.equals(request.enabled()) || request.role() == AdminRole.VIEWER)) {
            throw new IllegalArgumentException("You cannot disable or demote your own account");
        }
        if (request.role() != null) {
            user.setRole(request.role());
        }
        if (request.enabled() != null) {
            user.setEnabled(request.enabled());
        }
        if (request.password() != null) {
            user.setPasswordHash(passwordEncoder.encode(request.password()));
        }
        user.touch();
        return toResponse(user);
    }

    private String currentUsername() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        return authentication != null ? authentication.getName() : "";
    }

    private AdminUserResponse toResponse(AdminUser user) {
        return new AdminUserResponse(user.getId(), user.getUsername(), user.getRole(), user.isEnabled());
    }
}
