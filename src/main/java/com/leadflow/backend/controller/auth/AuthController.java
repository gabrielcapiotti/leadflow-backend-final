package com.leadflow.backend.controller.auth;

import com.leadflow.backend.entities.auth.UserSession;
import com.leadflow.backend.dto.auth.*;
import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.multitenancy.service.TenantService;
import com.leadflow.backend.repository.tenant.TenantRepository;
import com.leadflow.backend.security.CustomUserDetails;
import com.leadflow.backend.security.exception.UnauthorizedException;
import com.leadflow.backend.security.jwt.JwtService;
import com.leadflow.backend.security.jwt.JwtToken;
import com.leadflow.backend.service.auth.AuthService;
import com.leadflow.backend.service.auth.RefreshTokenService;
import com.leadflow.backend.service.auth.UserSessionService;
import com.leadflow.backend.service.vendor.VendorService;
import com.leadflow.backend.service.vendor.UsageService;
import com.leadflow.backend.entities.Plan;
import com.leadflow.backend.entities.Tenant;
import com.leadflow.backend.repository.PlanRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;

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
    private final TenantRepository tenantRepository;
    private final VendorService vendorService;
    private final UsageService usageService;
    private final PlanRepository planRepository;
    private final AuthenticationManager authenticationManager;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            UserSessionService userSessionService,
            TenantService tenantService,
            TenantRepository tenantRepository,
            VendorService vendorService,
            UsageService usageService,
            PlanRepository planRepository,
            AuthenticationManager authenticationManager
    ) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userSessionService = userSessionService;
        this.tenantService = tenantService;
        this.tenantRepository = tenantRepository;
        this.vendorService = vendorService;
        this.usageService = usageService;
        this.planRepository = planRepository;
        this.authenticationManager = authenticationManager;
    }

    /* ======================================================
       REGISTER
       ====================================================== */

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest
    ) {

        // 🔧 FIX: Separate tenantId (UUID) from schemaName (schema identifier)
        // - tenantId: UUID from Tenant table - primary identifier for user
        // - schemaName: "t_" + UUID without hyphens - valid PostgreSQL schema name
        String schemaName = "t_" + UUID.randomUUID().toString().replace("-", "");
        
        log.info("Generating new tenant for registration: schema={} | Email: {}", 
                 schemaName, maskEmail(request.email()));

        // ✅ CRITICAL FIX: Create Tenant record in database and GET its actual ID
        // The Tenant.id from DB is the TRUE tenantId that must be stored in User.tenantId
        UUID tenantId;
        try {
            Tenant createdTenant = tenantService.createTenant(schemaName);
            tenantId = createdTenant.getId();
            log.info("✓ Tenant record created in database: id={}, schema={}", tenantId, schemaName);
        } catch (IllegalArgumentException e) {
            // Schema validation failed - let it propagate as BAD_REQUEST (400)
            log.error("❌ Schema validation failed during tenant creation: {}", e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("❌ CRITICAL: Tenant creation failed during registration: {}", e.getMessage(), e);
            throw new IllegalStateException("Tenant creation failed - registration incomplete", e);
        }

        log.info("User registration attempt: {}", maskEmail(request.email()));

        User user = authService.registerUser(
                request.name(),
                request.email(),
                request.password(),
                tenantId.toString()
        );

        // ✅ ORQUESTRAÇÃO CRÍTICA: Criar vendor APENAS durante registro
        try {
            var vendor = vendorService.createVendor(user);
            log.info("✓ Vendor created successfully for new user: {} (vendor={})", user.getId(), vendor.getId());

            // ✅ Inicializar usage com plano padrão
            try {
                Plan defaultPlan = planRepository.findByActiveTrue()
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No active plan found"));
                usageService.initializeUsage(vendor.getId(), defaultPlan);
                log.info("✓ Usage initialized successfully for vendor: {}", vendor.getId());
            } catch (Exception e) {
                log.warn("⚠️  Usage initialization failed (non-critical): {}", e.getMessage());
                // Não interrompe o fluxo - vendor foi criado OK
            }
        } catch (Exception e) {
            log.error("❌ Vendor creation failed during registration: {}", e.getMessage(), e);
            throw new IllegalStateException("Vendor initialization failed - registration incomplete", e);
        }

        // ✅ CRITICAL FIX: Pass tenantId (UUID), NOT schemaName, to generateToken
        // JWT must contain the SAME tenant value that will be sent in X-Tenant-ID header
        // Otherwise: JWT extraction returns schemaName, header has UUID → mismatch → 401
        JwtToken accessToken = jwtService.generateToken(user, tenantId.toString());

        try {
            createSession(user.getId(), tenantId.toString(), accessToken, httpRequest);
            log.info("✓ Session created successfully for new user: {} (tenantId={})", user.getId(), tenantId);
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

        // ✅ CRITICAL: Return the TRUE tenantId (UUID), not schemaName
        // schemaName is "t_...", but X-Tenant-ID header expects UUID
        // Vendor.tenantId is stored as UUID string in database
        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(new AuthResponse(accessToken.getToken(), refreshToken, tenantId.toString()));
    }

    /* ======================================================
       LOGIN
       ====================================================== */

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest
    ) {
        // 🔒 CRITICAL FIX: Login is PUBLIC - cannot use TenantContext (JWT doesn't exist yet)
        // Must receive tenant from header or request body
        String tenant = extractTenantFromRequest(httpRequest);
        
        if (tenant == null || tenant.isBlank()) {
            log.error("❌ Login failed: No tenant provided in request");
            throw new UnauthorizedException("Tenant ID is required for login");
        }

        // ✅ FIX: Convert schemaName to tenantId
        // - If tenant starts with "t_", it's a schemaName → query DB to get tenantId (UUID)
        // - Otherwise, assume it's already tenantId (UUID)
        String resolvedTenantId = tenant;
        if (tenant.startsWith("t_")) {
            log.info("🔍 [LOGIN] Tenant from header is schemaName (t_...). Querying DB for ACTIVE tenants only...");
            log.info("🔍 [LOGIN] Query: findBySchemaNameIgnoreCaseAndDeletedAtIsNull('{}') [soft-delete aware]", tenant);
            
            // ✅ CRITICAL FIX: Use soft-delete aware query to exclude deleted tenants
            // This prevents stale/deleted tenant records from being returned
            var foundTenant = tenantRepository.findBySchemaNameIgnoreCaseAndDeletedAtIsNull(tenant);
            if (foundTenant.isPresent()) {
                Tenant tenantEntity = foundTenant.get();
                resolvedTenantId = tenantEntity.getId().toString();
                log.info("✓ [LOGIN] Found ACTIVE Tenant in DB: id={}, schemaName={}", 
                    tenantEntity.getId(), tenantEntity.getSchemaName());
            } else {
                log.error("❌ [LOGIN] ACTIVE Tenant NOT found in database for schemaName: {} (may be soft-deleted)", tenant);
                throw new UnauthorizedException("Invalid tenant");
            }
        } else {
            log.info("ℹ️ [LOGIN] Tenant from header is already UUID format (no t_ prefix): {}", resolvedTenantId);
        }

        log.info("Login attempt for user: {} (tenant={})", maskEmail(request.email()), resolvedTenantId);

        // ✅ CRITICAL FIX: AuthenticationManager.authenticate() calls UserDetailsServiceImpl
        // UserDetailsService will:
        // 1. Find user by email (NO tenant filter)
        // 2. Extract tenant from user record
        // 3. Set TenantContext with user's tenant
        // 4. Validate password
        // Therefore: DO NOT pre-set TenantContext here
        
        try {
            Authentication authentication = authenticationManager.authenticate(
                    new UsernamePasswordAuthenticationToken(
                            request.email(),
                            request.password()
                    )
            );

            CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();
            User user = userDetails.getUser();

            log.info("User authenticated successfully via Spring Security: {}", user.getId());

            JwtToken accessToken = jwtService.generateToken(user, tenant);
            
            log.info("🔐 Generated new accessToken with tokenId={} for user={}", 
                accessToken.getTokenId(), user.getId());

            // Session creation must not fail - if it does, it's a critical auth system issue
            try {
                createSession(user.getId(), resolvedTenantId, accessToken, httpRequest);
                log.info("✅ Session created successfully for user: {} (tenantId={})", user.getId(), resolvedTenantId);
            } catch (Exception e) {
                log.error("❌ CRITICAL: Session creation failed for user={}. Error={}", user.getId(), e.getMessage(), e);
                throw e;  // Re-throw to be handled by GlobalExceptionHandler
            }

            String refreshToken = refreshTokenService.generate(
                    user,
                    getClientIpAddress(httpRequest),
                    httpRequest.getHeader("User-Agent")
            );

            log.info("User {} logged in successfully", user.getId());

            return ResponseEntity.ok(
                    new AuthResponse(accessToken.getToken(), refreshToken, tenant)
            );
        } finally {
            TenantContext.clear();
        }
    }

    /* ======================================================
       CURRENT USER
       ====================================================== */

    @GetMapping("/me")
    public ResponseEntity<Map<String, Object>> me(Authentication authentication) {

        CustomUserDetails user = requireAuthenticatedUser(authentication);

        // Get tenant from TenantContext (single source of truth)
        String tenantId = TenantContext.requireTenant();

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getUsername(),
                "role", user.getAuthorities().stream()
                        .findFirst()
                        .map(a -> a.getAuthority())
                        .orElse("ROLE_USER"),
                "tenantId", tenantId
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

        // 🔒 CRITICAL FIX: Refresh is PUBLIC - cannot use TenantContext
        // First validate and rotate the refresh token
        RefreshTokenService.RotationResult result =
                refreshTokenService.validateAndRotate(
                        request.refreshToken(),
                        getClientIpAddress(httpRequest),
                        httpRequest.getHeader("User-Agent")
                );
        
        log.debug("✓ Refresh token validated and rotated");
        
        // ✅ NOW extract tenant from the user we got back (has tenant from DB)
        String tenant = result.user().getTenantId();
        log.debug("✓ Tenant extracted from user: {}", tenant);
        
        // ✅ CRITICAL FIX: Refresh is PUBLIC and just renews JWT
        // It should NOT modify the session in the database
        // Session persists independently - only login creates, logout destroys
        
        log.debug("Generating new JWT token for user: {}", result.user().getEmail());
        
        // Generate NEW JWT with NEW tokenId (generates fresh identity)
        JwtToken newAccessToken = jwtService.generateTokenForRefresh(
                result.user(), 
                tenant
        );
        
        log.info("✓ New access token generated with fresh tokenId: {}", newAccessToken.getTokenId());

        return ResponseEntity.ok(
                new AuthResponse(newAccessToken.getToken(), result.newRefreshToken(), tenant)
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
       REGISTER ADMIN (PROTECTED)
       ====================================================== */

    @PostMapping("/register-admin")
    public ResponseEntity<AuthResponse> registerAdmin(
            @Valid @RequestBody RegisterRequest request,
            @RequestHeader(value = "X-Internal-Secret", required = false) String secret,
            HttpServletRequest httpRequest
    ) {

        // 🔐 SECURITY CHECK: Validate internal secret
        String expectedSecret = System.getenv("ADMIN_REGISTER_SECRET");
        if (expectedSecret == null || expectedSecret.isBlank()) {
            expectedSecret = "SUPER_SECRET_KEY_CHANGE_ME";  // Default for dev - MUST override in prod
        }

        if (secret == null || !secret.equals(expectedSecret)) {
            log.warn("❌ Unauthorized admin registration attempt - invalid secret");
            throw new UnauthorizedException("Invalid or missing X-Internal-Secret header");
        }

        // 🔒 Extract tenant from header
        String tenant = extractTenantFromRequest(httpRequest);

        if (tenant == null || tenant.isBlank()) {
            log.error("❌ Admin registration failed: No tenant provided in request");
            throw new UnauthorizedException("Tenant ID is required");
        }

        log.info("🔑 Registering ADMIN user for tenant: {} | Email: {}", tenant, maskEmail(request.email()));

        // ✅ Register user with ADMIN role
        User user = authService.registerAdmin(
                request.name(),
                request.email(),
                request.password(),
                tenant
        );

        // ✅ Generate JWT token for immediate use
        TenantContext.setTenant(tenant);
        try {
            JwtToken accessToken = jwtService.generateToken(user, tenant);

            String refreshToken = refreshTokenService.generate(
                    user,
                    getClientIpAddress(httpRequest),
                    httpRequest.getHeader("User-Agent")
            );

            log.info("✅ ADMIN user registered successfully: {} (tenant={})", user.getEmail(), tenant);

            return ResponseEntity.status(HttpStatus.CREATED).body(
                    new AuthResponse(accessToken.getToken(), refreshToken, tenant)
            );
        } finally {
            TenantContext.clear();
        }
    }

    /* ======================================================
       CHANGE PASSWORD
       ====================================================== */

    @PostMapping("/change-password")
    public ResponseEntity<Map<String, Object>> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Authentication authentication
    ) {

        CustomUserDetails user = requireAuthenticatedUser(authentication);

        authService.validatePassword(
                user.getUsername(),
                request.currentPassword()
        );

        // Revoga todas as sessões (segurança intacta)
        authService.changePassword(
                user.getId(),
                request.newPassword()
        );

        // Fluxo limpo: 200 OK + cliente trata re-login
        return ResponseEntity.ok(Map.of(
                "message", "Password changed successfully. Please login again.",
                "requiresReauthentication", true
        ));
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
        // Single source of truth: TenantContext (from JWT)
        return TenantContext.requireTenant();
    }

    private String resolveTenant(HttpServletRequest request) {
        // Single source of truth: TenantContext (from JWT via TenantFilter)
        // No fallback to header - TenantResolver already validated it
        return TenantContext.requireTenant();
    }

    private String validateTenant(String tenant, String source) {
        
        if (tenant == null || tenant.isBlank()) {
            log.error("❌ TENANT VALIDATION FAILED: {} returned null/blank", source);
            throw new UnauthorizedException("Tenant required");
        }

        // Check for UUID pattern (0000000-0000-0000-0000-000000000000)
        if (tenant.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
            // If it's all zeros, it's definitely wrong
            if (tenant.equals("00000000-0000-0000-0000-000000000000")) {
                log.error("❌ CRITICAL: {} returned empty UUID (00000000-0000-0000-0000-000000000000)", source);
                throw new UnauthorizedException("Invalid tenant");
            }
            // Even if not all zeros, warn about UUID usage (should be String schema names)
            log.warn("⚠️  {} returned UUID format ({}), expected String schema name", source, tenant);
        }

        log.debug("✓ Tenant validated from {}: {}", source, tenant);
        return tenant;
    }

    private String extractTenantFromRequest(HttpServletRequest request) {
        // 🔒 IMPORTANT: For public endpoints (login/refresh), tenant must come from:
        // 1. X-Tenant-ID header (preferred)
        // 2. Query parameter ?tenantId=...
        
        String tenantFromHeader = request.getHeader("X-Tenant-ID");
        if (tenantFromHeader != null && !tenantFromHeader.isBlank()) {
            log.debug("✓ Tenant resolved from X-Tenant-ID header: {}", tenantFromHeader);
            return tenantFromHeader;
        }
        
        String tenantFromQuery = request.getParameter("tenantId");
        if (tenantFromQuery != null && !tenantFromQuery.isBlank()) {
            log.debug("✓ Tenant resolved from query param: {}", tenantFromQuery);
            return tenantFromQuery;
        }
        
        return null;  // Client must provide tenant via header or query param
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