package com.leadflow.backend.config;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import com.leadflow.backend.entities.user.Role;
import com.leadflow.backend.repository.user.RoleRepository;

/**
 * Initializes default roles in the database at application startup.
 * Ensures ROLE_USER, ROLE_ADMIN, and ROLE_VENDOR exist for user assignment.
 */
@Component
@RequiredArgsConstructor
public class RoleInitializer {

    private static final Logger log = LoggerFactory.getLogger(RoleInitializer.class);

    private final RoleRepository roleRepository;

    @PostConstruct
    public void initializeRoles() {
        try {
            // Initialize ROLE_USER
            if (!roleRepository.existsByName("ROLE_USER")) {
                roleRepository.save(new Role("ROLE_USER"));
                log.info("Role created: ROLE_USER");
            }

            // Initialize ROLE_ADMIN
            if (!roleRepository.existsByName("ROLE_ADMIN")) {
                roleRepository.save(new Role("ROLE_ADMIN"));
                log.info("Role created: ROLE_ADMIN");
            }

            // Initialize ROLE_VENDOR
            if (!roleRepository.existsByName("ROLE_VENDOR")) {
                roleRepository.save(new Role("ROLE_VENDOR"));
                log.info("Role created: ROLE_VENDOR");
            }

            log.info("✅ All default roles initialized successfully");
        } catch (Exception e) {
            log.error("❌ Error initializing roles", e);
        }
    }
}
