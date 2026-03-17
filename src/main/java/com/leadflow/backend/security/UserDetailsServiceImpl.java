package com.leadflow.backend.security;

import com.leadflow.backend.entities.user.User;
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

        log.debug("Loading user details for email: {}", normalizedEmail);

        User user = userRepository
                .findByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> {
                    log.warn("User not found in database: {}", normalizedEmail);
                    return new UsernameNotFoundException("User not found");
                });

        validateUser(user);

        log.debug("User details loaded successfully for: {}", normalizedEmail);
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
            throw new IllegalStateException("User password not configured");
        }

        if (user.getRole() == null || user.getRole().getName() == null) {
            throw new IllegalStateException("User role not configured properly");
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

        String normalized = email.trim();

        if (normalized.isBlank()) {
            throw new UsernameNotFoundException("Email cannot be blank");
        }

        return normalized;
    }
}