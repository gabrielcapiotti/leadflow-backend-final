package com.leadflow.backend.service.user;

import com.leadflow.backend.entities.user.Role;
import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.entities.vendor.Vendor;
import com.leadflow.backend.exception.UserNotFoundException;
import com.leadflow.backend.repository.user.RoleRepository;
import com.leadflow.backend.repository.user.UserRepository;
import com.leadflow.backend.security.CustomUserDetails;
import com.leadflow.backend.service.vendor.UsageService;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final UsageService usageService;

    public UserService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            UsageService usageService
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.usageService = usageService;
    }

    /* ======================================================
       READ
       ====================================================== */

    @Transactional(readOnly = true)
    public Page<User> listActiveUsers(Pageable pageable) {

        if (pageable == null) {
            throw new IllegalArgumentException("Pageable cannot be null");
        }

        return userRepository.findAllByDeletedAtIsNull(pageable);
    }

    @Transactional(readOnly = true)
    public User getByIdOrThrow(UUID id) {

        if (id == null) {
            throw new IllegalArgumentException("User id cannot be null");
        }

        return userRepository
                .findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() ->
                        new UserNotFoundException("User not found with id: " + id)
                );
    }

    @Transactional(readOnly = true)
    public User getActiveByEmail(String email) {

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank");
        }

        return userRepository
                .findByEmailIgnoreCaseAndDeletedAtIsNull(email.trim().toLowerCase())
                .orElseThrow(() ->
                        new UserNotFoundException("User not found with email: " + email)
                );
    }

    /* ======================================================
       UPDATE
       ====================================================== */

    @Transactional
    public User updateUser(UUID id, String name, String email, UUID roleId) {

        if (id == null || roleId == null) {
            throw new IllegalArgumentException("Id and roleId cannot be null");
        }

        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Name cannot be blank");
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank");
        }

        // ===== OWNERSHIP VALIDATION (Fallback) =====
        // Verify user is either ADMIN or updating themselves
        validateUpdateAuthorization(id);

        User user = getByIdOrThrow(id);

        Role role = roleRepository.findById(roleId)
                .orElseThrow(() ->
                        new IllegalArgumentException("Role not found")
                );

        String normalizedEmail = email.trim().toLowerCase();

        boolean emailExists = userRepository
                .existsByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail);

        if (!user.getEmail().equalsIgnoreCase(normalizedEmail) && emailExists) {
            throw new IllegalArgumentException("Email already in use");
        }

        // Domain methods
        user.changeName(name.trim());
        user.changeEmail(normalizedEmail);
        user.changeRole(role);

        // Persist changes
        return userRepository.save(user);
    }

    /* ======================================================
       SOFT DELETE
       ====================================================== */

    @Transactional
    public void softDelete(UUID id) {

        User user = getByIdOrThrow(id);

        if (user.isDeleted()) {
            return; // idempotente
        }

        user.softDelete();
    }

    /* ======================================================
       CREATE ADMIN USER
       ====================================================== */

    @Transactional
    public void createAdminUser(Vendor vendor, String email) {

        if (vendor == null) {
            throw new IllegalArgumentException("Vendor cannot be null");
        }

        if (email == null || email.isBlank()) {
            throw new IllegalArgumentException("Email cannot be blank");
        }

        String normalizedEmail = email.trim().toLowerCase();

        if (userRepository.existsByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)) {
            throw new IllegalArgumentException("Email already exists");
        }

        Role role = roleRepository
                .findByNameIgnoreCase("ROLE_ADMIN")
                .or(() -> roleRepository.findByNameIgnoreCase("ROLE_USER"))
                .orElseThrow(() -> new IllegalStateException("No default role found"));

        String temporaryPassword = "tmp-" + UUID.randomUUID();

        User admin = new User(
                vendor.getNomeVendedor() != null
                        ? vendor.getNomeVendedor()
                        : normalizedEmail,
                normalizedEmail,
                passwordEncoder.encode(temporaryPassword),
                role
        );

        userRepository.save(admin);

        // Executa após persistência (consistência)
        usageService.consumeUser(vendor.getId());
    }

    /* ======================================================
       HELPER: AUTHORIZATION VALIDATION (Fallback)
       ====================================================== */

    /**
     * Validate that the current user is authorized to update the target user.
     * Only admins or the user themselves can update.
     *
     * @param targetUserId The user ID being updated
     * @throws AccessDeniedException if not authorized
     */
    private void validateUpdateAuthorization(UUID targetUserId) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null || !(authentication.getPrincipal() instanceof CustomUserDetails)) {
            throw new AccessDeniedException("User not authenticated");
        }

        CustomUserDetails currentUser = (CustomUserDetails) authentication.getPrincipal();
        UUID currentUserId = currentUser.getId();

        // Check if user is admin
        boolean isAdmin = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch(auth -> auth.equals("ROLE_ADMIN"));

        // Allow if: (1) is admin OR (2) is updating own profile
        if (!isAdmin && !currentUserId.equals(targetUserId)) {
            throw new AccessDeniedException(
                    "You can only update your own profile. Contact an administrator for other updates."
            );
        }
    }
}