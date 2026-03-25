package com.leadflow.backend.controller.auth;

import com.leadflow.backend.entities.auth.UserSession;
import com.leadflow.backend.dto.auth.*;
import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.multitenancy.service.TenantService;
import com.leadflow.backend.security.CustomUserDetails;
import com.leadflow.backend.security.exception.UnauthorizedException;
import com.leadflow.backend.security.jwt.JwtService;
import com.leadflow.backend.security.jwt.JwtToken;
import com.leadflow.backend.service.auth.AuthService;
import com.leadflow.backend.service.auth.RefreshTokenService;
import com.leadflow.backend.service.auth.UserSessionService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;

import org.springframework.web.bind.annotation.*;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

    private static final Logger log = LoggerFactory.getLogger(AuthController.class);

    private final AuthService authService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private final UserSessionService userSessionService;
    private final TenantService tenantService;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            UserSessionService userSessionService,
            TenantService tenantService
    ) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userSessionService = userSessionService;
        this.tenantService = tenantService;
    }

    /* ======================================================
       REGISTER
       ====================================================== */

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {

        String tenant = resolveTenant(httpRequest);

        log.info("User registration attempt: {}", maskEmail(request.email()));

        User user = authService.registerUser(
                request.name(),
                request.email(),
                request.password(),
                tenant
        );

        JwtToken accessToken = jwtService.generateToken(user, tenant);

        try {
            createSession(user.getId(), tenant, accessToken, httpRequest);
            log.info("✓ Session created successfully for new user: {} (tenantId={})", user.getId(), tenant);
        } catch (Exception e) {
            log.error("❌ CRITICAL: Session creation failed during registration for user: {} - {}", 
                user.getId(), e.getMessage(), e);
            throw new IllegalStateException("Session creation failed - registration incomplete", e);
        }

        String refreshToken = refreshTokenService.generate(
                user,
                getClientIpAddress(httpRequest),
                httpRequest.getHeader("User-Agent")
        );

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new AuthResponse(accessToken.getToken(), refreshToken));
    }

    /* ======================================================
       LOGIN
       ====================================================== */

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {

        String tenant = resolveTenant(httpRequest);

        log.info("Login attempt for user: {}", maskEmail(request.email()));

        User user = authService.authenticateUser(
                request.email(),
                request.password(),
                tenant
        );

        JwtToken accessToken = jwtService.generateToken(user, tenant);

        try {
            createSession(user.getId(), tenant, accessToken, httpRequest);
            log.info("✓ Session created successfully for user: {} (tenantId={})", user.getId(), tenant);
        } catch (Exception e) {
            log.error("❌ CRITICAL: Session creation failed during login for user: {} - {}", 
                user.getId(), e.getMessage(), e);
            throw new IllegalStateException("Session creation failed - authentication incomplete", e);
        }

        String refreshToken = refreshTokenService.generate(
                user,
                getClientIpAddress(httpRequest),
                httpRequest.getHeader("User-Agent")
        );

        log.info("User {} logged in successfully", user.getId());

        return ResponseEntity.ok(
                new AuthResponse(accessToken.getToken(), refreshToken)
        );
    }

    /* ======================================================
       CURRENT USER
       ====================================================== */

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {

        CustomUserDetails user = requireAuthenticatedUser(authentication);

        // Obter tenant do contexto
        String tenantId = TenantContext.getOrDefault();

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getUsername(),
                "role", user.getAuthorities().stream()
                        .findFirst()
                        .map(a -> a.getAuthority())
                        .orElse("ROLE_USER"),
                "tenantId", tenantId != null ? tenantId : "public"
        ));
    }

    /* ======================================================
       SESSIONS
       ====================================================== */

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> sessions(
            Authentication authentication,
            HttpServletRequest request
    ) {

        CustomUserDetails user = requireAuthenticatedUser(authentication);

        String tenant = resolveTenant();

        String tokenId = extractTokenId(request);

        List<SessionResponse> sessions =
                userSessionService.listActiveSessions(
                        user.getId(),
                        tenant,
                        tokenId
                );

        return ResponseEntity.ok(sessions);
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @PathVariable UUID sessionId,
            Authentication authentication
    ) {

        CustomUserDetails user = requireAuthenticatedUser(authentication);

        String tenant = resolveTenant();

        userSessionService.revokeSpecificSession(
                sessionId,
                user.getId(),
                tenant,
                null
        );

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Void> revokeAllSessions(Authentication authentication) {

        CustomUserDetails user = requireAuthenticatedUser(authentication);

        String tenant = resolveTenant();

        userSessionService.revokeAllUserSessions(user.getId(), tenant);

        return ResponseEntity.noContent().build();
    }

    /* ======================================================
       REFRESH TOKEN
       ====================================================== */

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshTokenRequest request,
            HttpServletRequest httpRequest
    ) {

        log.info("🔄 REFRESH TOKEN ENDPOINT: Starting refresh flow");

        String tenant = resolveTenant(httpRequest);
        log.debug("✓ Tenant resolved: {}", tenant);

        RefreshTokenService.RotationResult result =
                refreshTokenService.validateAndRotate(
                        request.refreshToken(),
                        getClientIpAddress(httpRequest),
                        httpRequest.getHeader("User-Agent")
                );
        
        log.debug("✓ Refresh token validated and rotated");
        
        // IMPORTANT: /refresh is a PUBLIC endpoint with no Authorization header
        // We need to find the active session to get its tokenId (session identifier)
        // The tokenId should persist across token renewals - only JWT expiration renews
        log.debug("Finding active session for user: {}", result.user().getEmail());
        List<UserSession> activeSessions = userSessionService.getActiveSessionsForUser(
                result.user().getId(),
                tenant
        );
        
        String sessionTokenId = null;
        if (!activeSessions.isEmpty()) {
            // Get tokenId from the most recent active session
            sessionTokenId = activeSessions.get(0).getTokenId();
            log.info("✓ Found active session with tokenId: {}", sessionTokenId);
        } else {
            log.error("❌ No active session found for user: {}", result.user().getEmail());
            throw new UnauthorizedException("No active session found");
        }

        // Generate NEW JWT with SAME tokenId as session (persist session across token renewals)
        JwtToken newAccessToken = jwtService.generateTokenForRefresh(
                result.user(), 
                tenant, 
                sessionTokenId
        );
        
        log.info("✓ New access token generated with reused sessionTokenId: {}", sessionTokenId);

        return ResponseEntity.ok(
                new AuthResponse(newAccessToken.getToken(), result.newRefreshToken())
        );
    }

    /* ======================================================
       LOGOUT
       ====================================================== */

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            Authentication authentication,
            HttpServletRequest request
    ) {

        CustomUserDetails user = requireAuthenticatedUser(authentication);

        String tenant = resolveTenant();

        String tokenId = extractTokenId(request);

        if (tokenId != null) {

            userSessionService.revokeSession(tokenId, tenant);
        }

        return ResponseEntity.noContent().build();
    }

    /* ======================================================
       CHANGE PASSWORD
       ====================================================== */

    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {

        CustomUserDetails user = requireAuthenticatedUser(authentication);

        authService.validatePassword(
                user.getUsername(),
                request.currentPassword()
        );

        authService.changePassword(
                user.getId(),
                request.newPassword()
        );

        String tenant = resolveTenant();

        userSessionService.revokeAllUserSessions(
                user.getId(),
                tenant
        );

        return ResponseEntity.noContent().build();
    }

    /* ======================================================
       DEBUG (DEV ONLY)
       ====================================================== */

    @Profile("dev")
    @GetMapping("/debug")
    public Map<String, Object> debug(Authentication authentication) {

        if (authentication == null) {
            return Map.of("authenticated", false);
        }

        return Map.of(
                "authenticated", authentication.isAuthenticated(),
                "principal", authentication.getPrincipal().getClass().getName(),
                "authorities", authentication.getAuthorities()
        );
    }

    /* ======================================================
       HELPERS
       ====================================================== */

    private CustomUserDetails requireAuthenticatedUser(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Authentication required");
        }

        if (authentication.getPrincipal() instanceof CustomUserDetails user) {
            return user;
        }

        throw new UnauthorizedException("Invalid authentication principal");
    }

    private String resolveTenant() {

        try {
            String tenant = TenantContext.getTenant();
            return validateTenant(tenant, "TenantContext");
        } catch (IllegalStateException e) {
            // Use default tenant (public) for public endpoints like /auth/register
            log.warn("TenantContext not set, using default: {}", e.getMessage());
            return "public";
        }
    }

    private String resolveTenant(HttpServletRequest request) {

        // First, try to get from TenantContext (set by TenantFilter)
        try {
            String tenant = TenantContext.getTenant();
            return validateTenant(tenant, "TenantContext");
        } catch (IllegalStateException e) {
            log.debug("TenantContext not set, checking header...");
            
            // For public endpoints, fall back to X-Tenant-ID header
            String headerTenant = request.getHeader("X-Tenant-ID");
            if (headerTenant != null && !headerTenant.isBlank()) {
                String validated = validateTenant(headerTenant, "X-Tenant-ID header");
                return validated;
            }
            
            // Default to "public" if nothing else is available
            log.debug("No tenant found in context or header, using default");
            return "public";
        }
    }

    private String validateTenant(String tenant, String source) {
        
        if (tenant == null || tenant.isBlank()) {
            log.error("❌ TENANT VALIDATION FAILED: {} returned null/blank", source);
            throw new IllegalStateException("Tenant cannot be null");
        }

        // Check for UUID pattern (0000000-0000-0000-0000-000000000000)
        if (tenant.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
            // If it's all zeros, it's definitely wrong
            if (tenant.equals("00000000-0000-0000-0000-000000000000")) {
                log.error("❌ CRITICAL: {} returned empty UUID (00000000-0000-0000-0000-000000000000)", source);
                throw new IllegalStateException("Invalid tenant identifier (empty UUID)");
            }
            // Even if not all zeros, warn about UUID usage (should be String schema names)
            log.warn("⚠️  {} returned UUID format ({}), expected String schema name", source, tenant);
        }

        log.debug("✓ Tenant validated from {}: {}", source, tenant);
        return tenant;
    }

    private String extractTokenId(HttpServletRequest request) {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null || !header.startsWith("Bearer ")) {
            return null;
        }

        try {
            return jwtService.extractTokenId(header.substring(7));
        } catch (Exception e) {
            return null;
        }
    }

    private void createSession(
            UUID userId,
            String tenantId,
            JwtToken token,
            HttpServletRequest request
    ) {

        userSessionService.createSession(
                userId,
                tenantId,
                token.getTokenId(),
                getClientIpAddress(request),
                request.getHeader("User-Agent")
        );
    }

    private String getClientIpAddress(HttpServletRequest request) {

        String xForwarded = request.getHeader("X-Forwarded-For");

        if (xForwarded != null && !xForwarded.isBlank()) {
            return xForwarded.split(",")[0].trim();
        }

        return request.getRemoteAddr();
    }

    private String maskEmail(String email) {

        int at = email.indexOf("@");

        if (at <= 2) {
            return "***" + email.substring(at);
        }

        return email.substring(0, 2) + "***" + email.substring(at);
    }
}