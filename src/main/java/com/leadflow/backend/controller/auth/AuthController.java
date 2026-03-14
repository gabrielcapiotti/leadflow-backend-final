package com.leadflow.backend.controller.auth;

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
import com.leadflow.backend.service.user.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

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
    private final UserService userService;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            UserSessionService userSessionService,
            TenantService tenantService,
            UserService userService
    ) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userSessionService = userSessionService;
        this.tenantService = tenantService;
        this.userService = userService;
    }

    /* ======================================================
       REGISTER
       ====================================================== */

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {
        log.info("New user registration: {}", request.email());
        
        String tenant = extractTenantFromHeader(httpRequest);
        if (tenant == null || tenant.isBlank()) {
            log.debug("No tenant provided, using default: public");
            tenant = "public";
        }
        
        // For registration, we don't require tenant to exist beforehand
        UUID tenantId = null;
        try {
            tenantId = tenantService.getTenantIdBySchema(tenant);
        } catch (Exception e) {
            log.debug("Tenant '{}' not found during registration, continuing without session", tenant);
        }

        User user = authService.registerUser(
                request.name(),
                request.email(),
                request.password(),
                tenant
        );

        JwtToken accessToken = jwtService.generateToken(user, tenant);

        // Se tenantId for null, apenas não cria session
        if (tenantId != null) {
            createSession(user.getId(), tenantId, accessToken, httpRequest);
        }

        String refreshToken = refreshTokenService.generate(
                user,
                httpRequest.getRemoteAddr(),
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
        String tenant = extractTenantFromHeader(httpRequest);
        if (tenant == null || tenant.isBlank()) {
            tenant = "public";
        }

        log.info("User login attempt: {} on tenant: {}", request.email(), tenant);

        User user = authService.authenticateUser(
                request.email(),
                request.password(),
                tenant
        );

        JwtToken accessToken = jwtService.generateToken(user, tenant);

        // Try to get tenant ID, but don't fail login if session creation fails
        UUID tenantId = null;
        try {
            tenantId = tenantService.getTenantIdBySchema(tenant);
        } catch (Exception e) {
            log.warn("Tenant '{}' not found during login, session creation skipped", tenant);
        }
        
        if (tenantId != null) {
            try {
                createSession(user.getId(), tenantId, accessToken, httpRequest);
                log.debug("Session created for user {} on tenant {}", user.getId(), tenant);
            } catch (Exception e) {
                log.error("Failed to create session for user {}: {}", user.getId(), e.getMessage());
                // Don't fail the login due to session creation failure
            }
        }

        String userAgent = httpRequest.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "unknown";
        }

        String ipAddress = getClientIpAddress(httpRequest);
        String refreshToken = refreshTokenService.generate(
                user,
                ipAddress,
                userAgent
        );

        log.info("User {} successfully logged in", user.getId());
        return ResponseEntity.ok(
                new AuthResponse(accessToken.getToken(), refreshToken)
        );
    }

    @GetMapping("/debug")
    public ResponseEntity<Map<String, Object>> debug(Authentication authentication) {
        Map<String, Object> debug = new java.util.HashMap<>();
        
        if (authentication == null) {
            debug.put("status", "No authentication object");
            return ResponseEntity.ok(debug);
        }
        
        debug.put("authenticated", authentication.isAuthenticated());
        debug.put("authorities", authentication.getAuthorities());
        debug.put("name", authentication.getName());
        
        Object principal = authentication.getPrincipal();
        debug.put("principal_class", principal == null ? "null" : principal.getClass().getName());
        debug.put("principal_toString", principal == null ? "null" : principal.toString());
        
        if (principal instanceof CustomUserDetails cu) {
            debug.put("is_CustomUserDetails", true);
            debug.put("user_id", cu.getId());
            debug.put("user_email", cu.getUsername());
        } else {
            debug.put("is_CustomUserDetails", false);
        }
        
        return ResponseEntity.ok(debug);
    }

    /* ======================================================
       CURRENT USER
       ====================================================== */

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {
        Map<String, Object> response = new java.util.HashMap<>();
        
        if (authentication == null || !authentication.isAuthenticated()) {
            response.put("error", "Not authenticated");
            return ResponseEntity.status(401).body(response);
        }
        
        Object principal = authentication.getPrincipal();
        
        if (principal instanceof CustomUserDetails user) {
            response.put("id", user.getId());
            response.put("email", user.getUsername());
            response.put("role", user.getAuthorities().stream()
                    .map(auth -> auth.getAuthority())
                    .findFirst()
                    .orElse("ROLE_USER"));
            return ResponseEntity.ok(response);
        } else {
            response.put("error", "Principal is not CustomUserDetails");
            response.put("principal_class", principal.getClass().getName());
            response.put("principal_toString", principal.toString());
            return ResponseEntity.status(500).body(response);
        }
    }

    /* ======================================================
       SESSIONS
       ====================================================== */

    @GetMapping("/sessions")
    public ResponseEntity<List<SessionResponse>> listSessions(
            Authentication authentication,
            HttpServletRequest request
    ) {
        try {
            log.debug("Listing sessions for authenticated user");

            CustomUserDetails user = requireAuthenticatedUser(authentication);

            String tenant = extractTenantFromHeader(request);
            if (tenant == null || tenant.isBlank()) {
                tenant = "public";
            }

            UUID tenantId = resolveTenantId(tenant);

            String token = extractToken(request);
            String tokenId = null;

            if (token != null) {
                try {
                    tokenId = jwtService.extractTokenId(token);
                } catch (Exception e) {
                    log.warn("Failed to extract token ID: {}", e.getMessage());
                }
            }
            
            List<SessionResponse> sessions =
                    userSessionService.listActiveSessions(
                            user.getId(),
                            tenantId,
                            tokenId
                    );

            log.info("User {} retrieved {} sessions", user.getId(), sessions != null ? sessions.size() : 0);
            return ResponseEntity.ok(sessions != null ? sessions : List.of());

        } catch (Exception e) {
            log.error("Failed to list sessions", e);
            throw new ResponseStatusException(
                    HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to retrieve sessions: " + e.getMessage(),
                    e
            );
        }
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @PathVariable UUID sessionId,
            Authentication authentication,
            HttpServletRequest request
    ) {
        CustomUserDetails user = requireAuthenticatedUser(authentication);

        String tenant = extractTenantFromHeader(request);
        if (tenant == null || tenant.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tenant identifier is required"
            );
        }
        
        UUID tenantId = resolveTenantId(tenant);
        
        String currentToken = extractToken(request);
        String currentTokenId = null;
        if (currentToken != null) {
            try {
                currentTokenId = jwtService.extractTokenId(currentToken);
            } catch (Exception e) {
                log.warn("Failed to extract current token ID during revoke: {}", e.getMessage());
            }
        }

        log.info("User {} revoking session {}", user.getId(), sessionId);
        userSessionService.revokeSpecificSession(
                sessionId,
                user.getId(),
                tenantId,
                currentTokenId
        );

        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/sessions")
    public ResponseEntity<Void> revokeAllSessions(
            Authentication authentication,
            HttpServletRequest request
    ) {
        CustomUserDetails user = requireAuthenticatedUser(authentication);

        String tenant = extractTenantFromHeader(request);
        if (tenant == null || tenant.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tenant identifier is required"
            );
        }
        
        UUID tenantId = resolveTenantId(tenant);

        log.info("User {} revoking all sessions (logout all devices)", user.getId());
        userSessionService.revokeAllUserSessions(
                user.getId(),
                tenantId
        );

        return ResponseEntity.noContent().build();
    }

    /* ======================================================
       HELPERS
       ====================================================== */

    private CustomUserDetails requireAuthenticatedUser(Authentication authentication) {

        if (authentication == null || !authentication.isAuthenticated()) {
            throw new UnauthorizedException("Authentication required");
        }

        Object principal = authentication.getPrincipal();

        if (principal instanceof CustomUserDetails user) {
            return user;
        }

        throw new UnauthorizedException(
            "Invalid authentication principal type: " + principal.getClass().getName()
        );
    }

    private UUID resolveTenantId(String tenant) {
        if (tenant == null || tenant.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tenant identifier is required"
            );
        }

        UUID tenantId = tenantService.getTenantIdBySchema(tenant);

        if (tenantId == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid tenant: " + tenant
            );
        }

        return tenantId;
    }

    private void createSession(
            UUID userId,
            UUID tenantId,
            JwtToken accessToken,
            HttpServletRequest request
    ) {
        // Get real IP address, considering proxies (Nginx, Cloudflare, etc.)
        String ipAddress = getClientIpAddress(request);
        String userAgent = request.getHeader("User-Agent");

        if (ipAddress == null || ipAddress.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "IP address cannot be null or blank"
            );
        }

        if (userAgent == null || userAgent.isBlank()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "User-Agent cannot be null or blank"
            );
        }

        log.debug("Creating session for user {} from IP {} with UA {}", userId, ipAddress, userAgent);
        userSessionService.createSession(
                userId,
                tenantId,
                accessToken.getTokenId(),
                ipAddress,
                userAgent
        );
    }

    /**
     * Get client IP address considering proxy headers.
     * Checks X-Forwarded-For first (used by Nginx, Cloudflare, etc.),
     * then falls back to remoteAddr.
     */
    private String getClientIpAddress(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            // X-Forwarded-For can contain multiple IPs, get the first one
            return xForwardedFor.split(",")[0].trim();
        }
        return request.getRemoteAddr();
    }

    private String requireTenant() {
        try {
            return TenantContext.getTenant();
        } catch (IllegalStateException e) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Tenant context not available"
            );
        }
    }

    private String extractTenantFromHeader(HttpServletRequest request) {
        String tenant = request.getHeader("X-Tenant-ID");
        
        if (tenant == null || tenant.isBlank()) {
            try {
                tenant = TenantContext.getTenant();
            } catch (IllegalStateException e) {
                log.debug("TenantContext not available, tenant will be null");
                tenant = null;
            }
        }
        
        return tenant;
    }

    private String extractToken(HttpServletRequest request) {

        String header = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (header == null) {
            return null;
        }

        if (!header.startsWith("Bearer ")) {
            return null;
        }

        return header.substring(7);
    }
}