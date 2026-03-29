package com.leadflow.backend.security;

import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.user.UserRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;
import java.util.UUID;

@Service
public class UserDetailsServiceImpl implements UserDetailsService {

    private static final Logger log = LoggerFactory.getLogger(UserDetailsServiceImpl.class);

    private final UserRepository userRepository;

    public UserDetailsServiceImpl(UserRepository userRepository) {
        this.userRepository = Objects.requireNonNull(
                userRepository,
                "userRepository cannot be null"
        );
    }

    /* ======================================================
       LOAD USER
       ====================================================== */

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email)
            throws UsernameNotFoundException {

        String normalizedEmail = normalizeEmail(email);

        UUID tenantId = TenantContext.getTenant();

        if (tenantId == null) {
            log.error("Tenant ID is missing during authentication");
            throw new UsernameNotFoundException("Tenant not provided");
        }

        log.info(
            "Loading user for authentication: email={}, tenant={}",
            normalizedEmail,
            tenantId
        );

        // CRITICAL FIX: Login is tenant-aware in multi-tenant architecture
        // Identity = (email + tenant_id). Email alone is NOT globally unique.
        // TenantFilter ensures tenant context is set before this method is called.
        User user = userRepository
                .findByEmailIgnoreCaseAndTenantIdAndDeletedAtIsNull(
                        normalizedEmail,
                        tenantId
                )
                .orElseThrow(() -> {
                    log.error(
                        "User NOT FOUND - email={}, tenant={}",
                        normalizedEmail,
                        tenantId
                    );
                    return new UsernameNotFoundException("User not found");
                });

        log.info(
                "User loaded successfully: email={}, tenant={}",
                normalizedEmail,
                tenantId
        );

        validateUser(user);

        return new CustomUserDetails(user);
    }

    /* ======================================================
       VALIDATION
       ====================================================== */

    private void validateUser(User user) {

        if (user.getDeletedAt() != null) {
            throw new UsernameNotFoundException("User not active");
        }

        if (user.getPassword() == null || user.getPassword().isBlank()) {
            throw new UsernameNotFoundException("Invalid credentials");
        }

        if (user.getRole() == null || user.getRole().getName() == null) {
            throw new UsernameNotFoundException("Invalid credentials");
        }

        if (user.isAccountLocked()) {
            throw new UsernameNotFoundException("User account locked");
        }
    }

    /* ======================================================
       HELPERS
       ====================================================== */

    private String normalizeEmail(String email) {

        if (email == null) {
            throw new UsernameNotFoundException("Email cannot be null");
        }

        String normalized = email.trim().toLowerCase();

        if (normalized.isBlank()) {
            throw new UsernameNotFoundException("Email cannot be blank");
        }

        return normalized;
    }
}