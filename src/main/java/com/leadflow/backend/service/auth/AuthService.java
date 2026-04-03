package com.leadflow.backend.service.auth;

import com.leadflow.backend.entities.audit.SecurityAction;
import com.leadflow.backend.entities.user.Role;
import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.user.RoleRepository;
import com.leadflow.backend.repository.user.UserRepository;
import com.leadflow.backend.security.exception.UnauthorizedException;
import com.leadflow.backend.service.audit.SecurityAuditService;
import com.leadflow.backend.service.vendor.VendorService;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;

import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Service
public class AuthService {

    private static final Logger logger =
            LoggerFactory.getLogger(AuthService.class);

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;
    private final SecurityAuditService auditService;
    private final LoginAuditService loginAuditService;
    private final BruteForceProtectionService bruteForceService;
    private final VendorService vendorService;
    private final UserSessionService userSessionService;

    private final int maxAttempts;
    private final int windowMinutes;

    public AuthService(
            UserRepository userRepository,
            RoleRepository roleRepository,
            PasswordEncoder passwordEncoder,
            SecurityAuditService auditService,
            LoginAuditService loginAuditService,
            BruteForceProtectionService bruteForceService,
            VendorService vendorService,
            UserSessionService userSessionService,
            @Value("${security.brute-force.max-attempts:5}") int maxAttempts,
            @Value("${security.brute-force.window-minutes:5}") int windowMinutes
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
        this.auditService = auditService;
        this.loginAuditService = loginAuditService;
        this.bruteForceService = bruteForceService;
        this.vendorService = vendorService;
        this.userSessionService = userSessionService;

        this.maxAttempts = Math.max(maxAttempts, 1);
        this.windowMinutes = Math.max(windowMinutes, 1);
    }

    /* ====================================================== */
    /* REGISTER                                               */
    /* ====================================================== */

    @Transactional
    public User registerUser(String name, String email, String password, java.util.UUID tenantId) {

        validateInput(name, email, password);

        String normalizedEmail = normalizeEmail(email);

        if (userRepository
                .existsByEmailIgnoreCaseAndTenantIdAndDeletedAtIsNull(normalizedEmail, tenantId)) {

            audit(SecurityAction.USER_REGISTERED, normalizedEmail, tenantId.toString(), false);
            throw new UnauthorizedException("Email already in use");
        }

        Role userRole = roleRepository
                .findByNameIgnoreCase("ROLE_USER")
                .orElseThrow(() ->
                        new IllegalStateException("Default role ROLE_USER not found")
                );

        User user = new User(
                name.trim(),
                normalizedEmail,
                passwordEncoder.encode(password),
                userRole
        );
        user.setTenantId(tenantId);

        User savedUser = userRepository.saveAndFlush(user); // 🔥 saveAndFlush garante visibilidade imediata

        // 🔥 CRÍTICO: Criar Vendor associado ao usuário
        vendorService.createVendor(savedUser);

        logger.info("User registered successfully: {} (tenant={})", normalizedEmail, tenantId);

        audit(SecurityAction.USER_REGISTERED, normalizedEmail, tenantId.toString(), true);

        return savedUser;
    }

    /* ====================================================== */
    /* REGISTER ADMIN (PROTECTED)                             */
    /* ====================================================== */

    @Transactional
    public User registerAdmin(String name, String email, String password, java.util.UUID tenantId) {

        validateInput(name, email, password);

        String normalizedEmail = normalizeEmail(email);

        if (userRepository
                .existsByEmailIgnoreCaseAndTenantIdAndDeletedAtIsNull(normalizedEmail, tenantId)) {

            audit(SecurityAction.USER_REGISTERED, normalizedEmail, tenantId.toString(), false);
            throw new UnauthorizedException("Email already in use");
        }

        // 🔐 Get ROLE_ADMIN - required for protected operations
        Role adminRole = roleRepository
                .findByNameIgnoreCase("ROLE_ADMIN")
                .orElseThrow(() -> {
                    logger.error("CRITICAL: ROLE_ADMIN not found in database!");
                    return new IllegalStateException("ROLE_ADMIN role not found - database may be misconfigured");
                });

        User user = new User(
                name.trim(),
                normalizedEmail,
                passwordEncoder.encode(password),
                adminRole
        );
        user.setTenantId(tenantId);

        User savedUser = userRepository.saveAndFlush(user);

        // 🔥 CRÍTICO: Criar Vendor associado ao usuário admin
        vendorService.createVendor(savedUser);

        logger.info("🔑 ADMIN user registered successfully: {} (tenant={})", normalizedEmail, tenantId);

        audit(SecurityAction.USER_REGISTERED, normalizedEmail, tenantId.toString(), true);

        return savedUser;
    }

    /* ====================================================== */
    /* AUTHENTICATE                                           */
    /* ====================================================== */

    @Transactional
    public User authenticateUser(String email, String password, String tenant) {

        HttpServletRequest request = currentRequest();

        if (email == null || email.isBlank()
                || password == null || password.isBlank()) {

            recordFailureAudit(email, "Invalid credentials", null);
            throw new UnauthorizedException("Invalid credentials");
        }

        String normalizedEmail = normalizeEmail(email);

        String ip = request != null ? request.getRemoteAddr() : "unknown";

        // ✅ CRITICAL FIX: Brute force keys NOT dependent on tenant
        // Tenant will be determined from the user record itself
        String emailKey = "bf:email:" + normalizedEmail;
        String ipKey = "bf:ip:" + ip;

        if (bruteForceService.isBlocked(emailKey, maxAttempts)
                || bruteForceService.isBlocked(ipKey, maxAttempts)) {

            recordFailureAudit(normalizedEmail, "Brute force detected", null);

            throw new UnauthorizedException(
                    "Too many failed attempts. Try again later."
            );
        }

        // ✅ CRITICAL FIX: Find user by email ONLY (no tenant filter)
        // Tenant is determined FROM the user, not from external context
        // This is the source of truth for tenant assignment
        User user = userRepository
                .findByEmailIgnoreCaseAndDeletedAtIsNull(normalizedEmail)
                .orElseThrow(() -> {
                    recordFailureAudit(normalizedEmail, "User not found", null);
                    return new UnauthorizedException("Invalid credentials");
                });

        // ✅ Extract tenant from user - this is THE authority
        final String tenantContext = user.getTenantId().toString();

        if (user.isAccountLocked()) {

            recordFailureAudit(normalizedEmail, "Account locked", tenantContext);

            throw new UnauthorizedException(
                    "Account temporarily locked. Try again later."
            );
        }

        if (!passwordEncoder.matches(password, user.getPassword())) {

            user.registerFailedLogin();
            userRepository.save(user);

            bruteForceService.recordFailure(emailKey, windowMinutes);
            bruteForceService.recordFailure(ipKey, windowMinutes);

            recordFailureAudit(normalizedEmail, "Wrong password", tenantContext);

            throw new UnauthorizedException("Invalid credentials");
        }

        user.resetLoginAttempts();
        userRepository.save(user);

        bruteForceService.reset(emailKey);
        bruteForceService.reset(ipKey);

        recordSuccessAudit(user, tenantContext);

        logger.info("User authenticated successfully: {} (tenant={})", normalizedEmail, tenantContext);

        return user;
    }

    /* ====================================================== */
    /* HELPERS                                                */
    /* ====================================================== */

    private void recordSuccessAudit(User user, String tenant) {
        HttpServletRequest request = currentRequest();

        loginAuditService.recordSuccess(
                user.getId(),
                tenant,
                user.getEmail(),
                request != null ? request.getRemoteAddr() : null,
                request != null ? request.getHeader("User-Agent") : null,
                false
        );

        audit(SecurityAction.LOGIN_SUCCESS, user.getEmail(), tenant, true);
    }

    private void recordFailureAudit(String email, String reason, String tenant) {
        HttpServletRequest request = currentRequest();

        loginAuditService.recordFailure(
                tenant,
                email,
                request != null ? request.getRemoteAddr() : null,
                request != null ? request.getHeader("User-Agent") : null,
                reason
        );

        audit(SecurityAction.LOGIN_FAILED, email, tenant, false);
    }

    private void audit(SecurityAction action, String email, String tenant, boolean success) {
        try {
            HttpServletRequest request = currentRequest();

            String auditTenant = tenant != null ? tenant : TenantContext.getIfPresent() != null ? TenantContext.getIfPresent().toString() : null;

            auditService.log(
                    action,
                    email,
                    auditTenant,
                    success,
                    request != null ? request.getRemoteAddr() : null,
                    request != null ? request.getHeader("User-Agent") : null,
                    MDC.get("correlationId")
            );
        } catch (Exception e) {
            logger.warn("Audit log failed: {}", e.getMessage());
        }
    }

    private void audit(SecurityAction action, String email, boolean success) {
        audit(action, email, null, success);
    }

    private HttpServletRequest currentRequest() {
        ServletRequestAttributes attributes =
                (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();

        return attributes != null ? attributes.getRequest() : null;
    }

    private String normalizeEmail(String email) {
        return email.trim().toLowerCase();
    }

    private void validateInput(String name, String email, String password) {

        if (name == null || name.isBlank())
            throw new UnauthorizedException("Name cannot be blank");

        if (email == null || email.isBlank())
            throw new UnauthorizedException("Email cannot be blank");

        if (password == null || password.length() < 8)
            throw new UnauthorizedException(
                    "Password must contain at least 8 characters"
            );
    }

    /* ====================================================== */
    /* PASSWORD MANAGEMENT                                    */
    /* ====================================================== */

    @Transactional(readOnly = true)
    public void validatePassword(String email, String password) {
        if (email == null || email.isBlank() || password == null || password.isBlank()) {
            throw new UnauthorizedException("Email and password cannot be blank");
        }

        String normalizedEmail = normalizeEmail(email);
        UUID tenant = TenantContext.requireTenant();

        User user = userRepository
                .findByEmailIgnoreCaseAndTenantIdAndDeletedAtIsNull(normalizedEmail, tenant)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        if (!passwordEncoder.matches(password, user.getPassword())) {
            throw new UnauthorizedException("Current password is incorrect");
        }
    }

    @Transactional
    public void changePassword(UUID userId, String newPassword) {
        if (userId == null) {
            throw new UnauthorizedException("User ID cannot be null");
        }

        if (newPassword == null || newPassword.length() < 8) {
            throw new UnauthorizedException(
                    "New password must contain at least 8 characters"
            );
        }

        UUID tenant = TenantContext.requireTenant();

        User user = userRepository
                .findByIdAndTenantIdAndDeletedAtIsNull(userId, tenant)
                .orElseThrow(() -> new UnauthorizedException("User not found"));

        user.changePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        logger.info("Password changed for user: {}", user.getEmail());
        audit(SecurityAction.PASSWORD_CHANGED, user.getEmail(), true);

        // Revoga TODAS as sessoes
        UUID tenantId = tenant;  // Use UUID directly without String conversion
        userSessionService.revokeAllUserSessions(userId, tenantId);
        logger.info("All sessions revoked for user after password change: {}", user.getEmail());

        // ✅ NÃO lança exception aqui
        // O cliente deve tratar isso e fazer login novamente
    }
}