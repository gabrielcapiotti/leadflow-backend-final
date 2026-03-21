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
import com.leadflow.backend.service.vendor.VendorService;
import com.leadflow.domain.auth.service.PasswordResetService;

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
    private final PasswordResetService passwordResetService;
    private final VendorService vendorService;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            UserSessionService userSessionService,
            TenantService tenantService,
            PasswordResetService passwordResetService,
            VendorService vendorService
    ) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userSessionService = userSessionService;
        this.tenantService = tenantService;
        this.passwordResetService = passwordResetService;
        this.vendorService = vendorService;
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

        // Garantir que Vendor existe após registro
        vendorService.ensureVendorExists(user.getEmail());

        JwtToken accessToken = jwtService.generateToken(user, tenant);

        UUID tenantId = tenantService.getTenantIdBySchema(tenant);

        createSession(user.getId(), tenantId, accessToken, httpRequest);

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

        // Garantir que Vendor existe após login (arquitetura: todo User autenticado tem Vendor)
        vendorService.ensureVendorExists(user.getEmail());

        JwtToken accessToken = jwtService.generateToken(user, tenant);

        UUID tenantId = tenantService.getTenantIdBySchema(tenant);

        createSession(user.getId(), tenantId, accessToken, httpRequest);

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

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getUsername(),
                "role", user.getAuthorities().stream()
                        .findFirst()
                        .map(a -> a.getAuthority())
                        .orElse("ROLE_USER")
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
        UUID tenantId = tenantService.getTenantIdBySchema(tenant);

        String tokenId = extractTokenId(request);

        List<SessionResponse> sessions =
                userSessionService.listActiveSessions(
                        user.getId(),
                        tenantId,
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

        UUID tenantId = tenantService.getTenantIdBySchema(resolveTenant());

        userSessionService.revokeSpecificSession(
                sessionId,
                user.getId(),
                tenantId,
                null
        );

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Void> revokeAllSessions(Authentication authentication) {

        CustomUserDetails user = requireAuthenticatedUser(authentication);

        UUID tenantId = tenantService.getTenantIdBySchema(resolveTenant());

        userSessionService.revokeAllUserSessions(user.getId(), tenantId);

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
        UUID tenantId = tenantService.getTenantIdBySchema(tenant);
        log.debug("✓ Tenant resolved: {} (ID: {})", tenant, tenantId);

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
                tenantId
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

        UUID tenantId = tenantService.getTenantIdBySchema(resolveTenant());

        String tokenId = extractTokenId(request);

        if (tokenId != null) {

            userSessionService.revokeSession(tokenId, tenantId);
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

        UUID tenantId = tenantService.getTenantIdBySchema(resolveTenant());

        userSessionService.revokeAllUserSessions(
                user.getId(),
                tenantId
        );

        return ResponseEntity.noContent().build();
    }

    /* ======================================================
       FORGOT PASSWORD
       ====================================================== */

    @PostMapping("/forgot-password")
    public ResponseEntity<Map<String, String>> forgotPassword(
            @Valid @RequestBody ForgotPasswordRequest request
    ) {
        log.info("Password reset requested for: {}", maskEmail(request.email()));

        // Always returns 200 (anti-enumeration: não revela se email existe)
        passwordResetService.requestPasswordReset(request.email());

        return ResponseEntity.ok(Map.of(
                "message", "Se o email existe, você receberá um link para resetar a senha"
        ));
    }

    /* ======================================================
       RESET PASSWORD
       ====================================================== */

    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(
            @Valid @RequestBody ResetPasswordRequest request
    ) {
        try {
            log.info("Password reset attempted with token");

            passwordResetService.resetPassword(request.token(), request.newPassword());

            log.info("Password reset successful");

            return ResponseEntity.noContent().build();
        } catch (IllegalArgumentException e) {
            log.warn("Password reset failed: {}", e.getMessage());
            throw new UnauthorizedException("Token inválido ou expirado");
        }
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
            return TenantContext.getTenant();
        } catch (IllegalStateException e) {
            // Use default tenant (public) for public endpoints like /auth/register
            return "public";
        }
    }

    private String resolveTenant(HttpServletRequest request) {

        // First, try to get from TenantContext (set by TenantFilter)
        try {
            return TenantContext.getTenant();
        } catch (IllegalStateException e) {
            // For public endpoints, fall back to X-Tenant-ID header
            String headerTenant = request.getHeader("X-Tenant-ID");
            if (headerTenant != null && !headerTenant.isBlank()) {
                return headerTenant;
            }
            // Default to "public" if nothing else is available
            return "public";
        }
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
            UUID tenantId,
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