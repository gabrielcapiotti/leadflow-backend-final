package com.leadflow.backend.controller.auth;

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
import com.leadflow.backend.service.vendor.UsageService;
import com.leadflow.backend.service.vendor.SubscriptionService;
import com.leadflow.backend.entities.Plan;
import com.leadflow.backend.entities.Tenant;
import com.leadflow.backend.repository.PlanRepository;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
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

    private final UsageService usageService;
    private final PlanRepository planRepository;
    private final AuthenticationManager authenticationManager;
    private final SubscriptionService subscriptionService;

    public AuthController(
            AuthService authService,
            JwtService jwtService,
            RefreshTokenService refreshTokenService,
            UserSessionService userSessionService,
            TenantService tenantService,
            TenantRepository tenantRepository,
            UsageService usageService,
            PlanRepository planRepository,
            AuthenticationManager authenticationManager,
            SubscriptionService subscriptionService
    ) {
        this.authService = authService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
        this.userSessionService = userSessionService;
        this.tenantService = tenantService;
        this.tenantRepository = tenantRepository;
        this.usageService = usageService;
        this.planRepository = planRepository;
        this.authenticationManager = authenticationManager;
        this.subscriptionService = subscriptionService;
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
                tenantId
        );

        // ✅ VENDOR criado no AuthService.registerUser() — sem duplicação aqui
        try {

            // 🔥 CREATE DEFAULT SUBSCRIPTION (novo)
            try {
                subscriptionService.createDefaultSubscription(tenantId);
                log.info("✓ Default subscription created successfully for tenant: {}", tenantId);
            } catch (Exception e) {
                log.warn("⚠️  Subscription creation failed (non-critical): {}", e.getMessage());
                // Não interrompe o fluxo - vendor foi criado OK
            }

            // ✅ Inicializar usage com plano padrão
            try {
                Plan defaultPlan = planRepository.findByActiveTrue()
                        .stream()
                        .findFirst()
                        .orElseThrow(() -> new IllegalStateException("No active plan found"));
                
                // 🔥 CRÍTICO: vendor.id = tenantId (alinhamento de identidade)
                UUID vendorId = UUID.fromString(user.getTenantId().toString());
                usageService.initializeUsage(vendorId, defaultPlan);
                log.info("✓ Usage initialized successfully for vendor: {}", vendorId);
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
            createSession(user.getId(), tenantId, accessToken, httpRequest);
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
        log.info("Login attempt for user: {}", maskEmail(request.email()));

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

            // ✅ CRITICAL FIX: Use user.getTenantId() DIRECTLY (UUID from authenticated user record)
            // SINGLE SOURCE OF TRUTH - no header, no conversion
            UUID tenantIdFromUser = user.getTenantId();
            
            if (tenantIdFromUser == null) {
                log.error("CRITICAL: User {} has NULL tenantId", user.getId());
                throw new IllegalStateException("User tenant association is null");
            }
            
            String tenantIdString = tenantIdFromUser.toString();
            log.debug("Tenant identity: userId={}, tenantId={}", user.getId(), tenantIdString);
            
            JwtToken accessToken = jwtService.generateToken(user, tenantIdString);
            
            log.info("🔐 Generated new accessToken with tokenId={} for user={}", 
                accessToken.getTokenId(), user.getId());

            // Session creation must not fail - if it does, it's a critical auth system issue
            try {
                // Re-fetch to validate consistency before session creation
                UUID validateTenantId = user.getTenantId();
                if (!validateTenantId.equals(tenantIdFromUser)) {
                    log.error("CRITICAL: Tenant ID mismatch! {} vs {}", tenantIdFromUser, validateTenantId);
                    throw new IllegalStateException("Tenant validation failed");
                }
                
                createSession(user.getId(), tenantIdFromUser, accessToken, httpRequest);
                log.info("Session created for user: {} (tenantId={})", user.getId(), tenantIdString);
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
                    new AuthResponse(accessToken.getToken(), refreshToken, tenantIdString)
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

        // 🔒 SECURITY: Get tenant from TenantContext (JWT-based, authoritative)
        UUID contextTenant = TenantContext.requireTenant();
        
        // 🔒 SECURITY: Validate user belongs to this tenant
        UUID userTenant = user.getUser().getTenantId();
        
        if (!contextTenant.equals(userTenant)) {
            log.warn(
                    "❌ SECURITY BREACH ATTEMPT: User {} from tenant {} tried to access tenant {} context",
                    user.getId(),
                    userTenant,
                    contextTenant
            );
            return ResponseEntity.status(HttpServletResponse.SC_FORBIDDEN).build();
        }

        return ResponseEntity.ok(Map.of(
                "id", user.getId(),
                "email", user.getUsername(),
                "role", user.getAuthorities().stream()
                        .findFirst()
                        .map(a -> a.getAuthority())
                        .orElse("ROLE_USER"),
                "tenantId", contextTenant.toString()
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

        UUID tenant = TenantContext.requireTenant();

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

        UUID tenant = TenantContext.requireTenant();

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

        UUID tenant = TenantContext.requireTenant();

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
        UUID tenantId = result.user().getTenantId();
        String tenant = tenantId.toString();
        log.debug("✓ Tenant extracted from user: {}", tenantId);
        
        // ✅ CRITICAL FIX: Refresh is PUBLIC and just renews JWT
        // It should NOT modify the session in the database
        // Session persists independently - only login creates, logout destroys
        
        log.debug("Generating new JWT token for user: {}", result.user().getEmail());
        
        // Generate NEW JWT with NEW tokenId (generates fresh identity)
        JwtToken newAccessToken = jwtService.generateTokenForRefresh(
                result.user(), 
                tenant  // Pass as String (already converted above)
        );
        
        log.info("✓ New access token generated with fresh tokenId: {}", newAccessToken.getTokenId());

        return ResponseEntity.ok(
                new AuthResponse(newAccessToken.getToken(), result.newRefreshToken(), tenant)  // tenant is String
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

        UUID tenant = TenantContext.requireTenant();

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
            Authentication authentication,
            HttpServletRequest httpRequest
    ) {

        boolean isInternalSecret = false;
        boolean isAuthenticatedAdmin = false;

        // 🔐 CHECK 1: Validate internal secret (for system setup)
        String expectedSecret = System.getenv("ADMIN_REGISTER_SECRET");
        if (expectedSecret == null || expectedSecret.isBlank()) {
            expectedSecret = "SUPER_SECRET_KEY_CHANGE_ME";  // Default for dev - MUST override in prod
        }

        if (secret != null && secret.equals(expectedSecret)) {
            isInternalSecret = true;
            log.info("Admin registration authorized via X-Internal-Secret");
        }

        // 🔐 CHECK 2: Validate authenticated ADMIN user
        if (authentication != null && authentication.isAuthenticated()) {
            boolean hasAdminRole = authentication.getAuthorities().stream()
                    .anyMatch(auth -> auth.getAuthority().equals("ROLE_ADMIN"));
            if (hasAdminRole) {
                isAuthenticatedAdmin = true;
                log.info("Admin registration authorized via authenticated ADMIN user");
            }
        }

        // 🔐 SECURITY: Require one of the two authorizations
        if (!isInternalSecret && !isAuthenticatedAdmin) {
            log.warn("❌ Unauthorized admin registration attempt");
            throw new UnauthorizedException(
                    "Unauthorized - requires either ADMIN authentication or X-Internal-Secret header"
            );
        }

        // 🔒 Extract tenant from header (for internal secret) or authentication (for admin user)
        String tenant;
        if (isAuthenticatedAdmin && authentication != null) {
            // Use tenant from authenticated admin's context
            tenant = TenantContext.getTenant().toString();  // getTenant() returns UUID, convert to String
            if (tenant == null || tenant.isBlank()) {
                tenant = extractTenantFromRequest(httpRequest);
            }
        } else {
            // Use tenant from header (internal secret flow)
            tenant = extractTenantFromRequest(httpRequest);
        }

        if (tenant == null || tenant.isBlank()) {
            log.error("Admin registration failed: No tenant provided in request");
            throw new UnauthorizedException("Tenant ID is required");
        }

        UUID tenantId = parseAndValidateTenantId(tenant); // Converts String (schemaName or UUID) to UUID

        log.info("🔑 Registering ADMIN user for tenant: {} | Email: {}", tenant, maskEmail(request.email()));

        // ✅ Register user with ADMIN role
        User user = authService.registerAdmin(
                request.name(),
                request.email(),
                request.password(),
                tenantId
        );

        // ✅ Generate JWT token for immediate use
        TenantContext.setTenant(tenantId);
        try {
            JwtToken accessToken = jwtService.generateToken(user, tenantId.toString());

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
        // Returns UUID as String (to keep compatibility with existing code)
        return TenantContext.requireTenant().toString();
    }

    private String resolveTenant(HttpServletRequest request) {
        // Single source of truth: TenantContext (from JWT via TenantFilter)
        // No fallback to header - TenantResolver already validated it
        return TenantContext.requireTenant().toString();  // Convert UUID to String
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
            UUID tenantId,
            JwtToken token,
            HttpServletRequest request
    ) {
        // Validate UUIDs are not null and properly formatted
        if (tenantId == null) {
            throw new IllegalStateException("tenantId cannot be null");
        }
        
        String tenantStr = tenantId.toString();
        if (!tenantStr.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
            log.error("CRITICAL: Invalid UUID format for tenantId: {}", tenantStr);
            throw new IllegalStateException("Invalid UUID format");
        }
        
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

    /**
     * Parse and validate tenant from String (header/param) to UUID
     * 
     * Input can be:
     * - UUID format: "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx" → returns as UUID
     * - SchemaName format: "t_..." → queries DB to get actual tenant UUID
     * 
     * @param tenant String tenant identifier (UUID or schemaName)
     * @return UUID tenant identifier from database
     * @throws UnauthorizedException if tenant is invalid or not found
     */
    private UUID parseAndValidateTenantId(String tenant) {
        
        if (tenant == null || tenant.isBlank()) {
            throw new UnauthorizedException("Tenant ID is required");
        }
        
        // Case 1: Input is schemaName (starts with "t_")
        if (tenant.startsWith("t_")) {
            log.debug("🔍 [PARSE_TENANT] Tenant from header is schemaName (t_...). Querying DB...");
            
            var foundTenant = tenantRepository.findBySchemaNameIgnoreCaseAndDeletedAtIsNull(tenant);
            if (foundTenant.isPresent()) {
                UUID tenantId = foundTenant.get().getId();
                log.debug("✓ [PARSE_TENANT] Found ACTIVE Tenant in DB: id={}, schemaName={}", tenantId, tenant);
                return tenantId;
            } else {
                log.error("❌ [PARSE_TENANT] ACTIVE Tenant NOT found in database for schemaName: {}", tenant);
                throw new UnauthorizedException("Invalid tenant");
            }
        }
        
        // Case 2: Input is UUID format
        try {
            UUID tenantId = UUID.fromString(tenant);
            log.debug("✓ [PARSE_TENANT] Tenant is UUID format: {}", tenantId);
            return tenantId;
        } catch (IllegalArgumentException e) {
            log.error("❌ [PARSE_TENANT] Invalid tenant format (not UUID, not schemaName): {}", tenant);
            throw new UnauthorizedException("Invalid tenant format");
        }
    }
}