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

        log.info("Loading user for authentication: email={}", normalizedEmail);

        // ✅ CRITICAL FIX: Find by email ONLY (NO tenant filter during login)
        // Users are stored in GLOBAL schema (public) with tenant_id field
        // Login must be tenant-agnostic - tenant comes from the user record, not request
        // This prevents dependency on external request context during authentication
        User user = userRepository
                .findByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> {
                    log.error("User NOT FOUND - email={}", normalizedEmail);
                    return new UsernameNotFoundException("User not found");
                });

        // ✅ Set tenant from user (source of truth)
        // Now we know the user's actual tenant, set it in context for password validation
        String userTenantId = user.getTenantId();
        TenantContext.setTenant(userTenantId);

        log.info(
                "User loaded successfully: email={}, tenant={}",
                normalizedEmail,
                userTenantId
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