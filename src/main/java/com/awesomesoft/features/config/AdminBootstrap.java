package com.awesomesoft.features.config;

import com.awesomesoft.features.domain.AdminRole;
import com.awesomesoft.features.domain.AdminUser;
import com.awesomesoft.features.infrastructure.AdminUserJpaRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/** Creates the initial ADMIN account when the admin_users table is empty (Grafana-style bootstrap). */
@Slf4j
@Component
@RequiredArgsConstructor
public class AdminBootstrap implements ApplicationRunner {

    private final AdminUserJpaRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    @Value("${features.admin.initial-username:admin}")
    private String initialUsername;

    @Value("${features.admin.initial-password:}")
    private String initialPassword;

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        if (userRepository.count() > 0) {
            return;
        }
        if (initialPassword == null || initialPassword.isBlank()) {
            log.warn("No admin users exist and features.admin.initial-password is blank - "
                    + "the panel will be inaccessible until a user is created");
            return;
        }
        userRepository.save(new AdminUser(initialUsername, passwordEncoder.encode(initialPassword),
                AdminRole.ADMIN));
        log.info("Bootstrap admin user '{}' created (change the password after first login)", initialUsername);
    }
}
