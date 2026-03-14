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

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/auth")
public class AuthController {

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

        System.out.println("🔥 REGISTER ENDPOINT ALCANÇADO!");
        
        String tenant = extractTenantFromHeader(httpRequest);
        if (tenant == null || tenant.isBlank()) {
            System.out.println("🔥 Tenant é nulo, setando como 'public'");
            tenant = "public";
        }
        
        // Para registro, não exigimos que o tenant exista previamente
        UUID tenantId = null;
        try {
            tenantId = tenantService.getTenantIdBySchema(tenant);
        } catch (Exception e) {
            System.out.println("🔥 Tenant '" + tenant + "' não encontrado, usando null");
            // Continua sem tenantId
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

        System.out.println("🔥 LOGIN tenant: " + tenant);
        System.out.println("🔥 LOGIN email: " + request.email());

        User user = authService.authenticateUser(
                request.email(),
                request.password(),
                tenant
        );

        JwtToken accessToken = jwtService.generateToken(user, tenant);

        // Try to get tenant ID, but don't fail if it doesn't exist
        UUID tenantId = null;
        try {
            tenantId = tenantService.getTenantIdBySchema(tenant);
        } catch (Exception e) {
            // Continue without session creation if tenant not found
        }
        
        if (tenantId != null) {
            try {
                createSession(user.getId(), tenantId, accessToken, httpRequest);
            } catch (Exception e) {
                // Log but don't fail the login
                System.err.println("Failed to create session: " + e.getMessage());
            }
        }

        String userAgent = httpRequest.getHeader("User-Agent");
        if (userAgent == null || userAgent.isBlank()) {
            userAgent = "unknown";
        }

        String refreshToken = refreshTokenService.generate(
                user,
                httpRequest.getRemoteAddr(),
                userAgent
        );

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

            System.out.println("[SESSION] 1. Inicio do listSessions");

            CustomUserDetails user = requireAuthenticatedUser(authentication);
            System.out.println("[SESSION] 2. Usuario autenticado: " + user.getId());

            String tenant = extractTenantFromHeader(request);
            if (tenant == null || tenant.isBlank()) {
                tenant = "public";
            }
            System.out.println("[SESSION] 3. Tenant: " + tenant);

            UUID tenantId = resolveTenantId(tenant);
            System.out.println("[SESSION] 4. TenantId: " + tenantId);

            String token = extractToken(request);
            System.out.println("[SESSION] 5. Token: " + (token != null ? "present" : "null"));
            
            String tokenId = null;

            if (token != null) {
                try {
                    tokenId = jwtService.extractTokenId(token);
                    System.out.println("[SESSION] 6. TokenId extraido: " + tokenId);
                } catch (Exception ignored) {
                    System.err.println("[SESSION] 6. Erro ao extrair tokenId: " + ignored.getMessage());
                }
            }

            System.out.println("[SESSION] 7. Chamando userSessionService.listActiveSessions");
            
            List<SessionResponse> sessions =
                    userSessionService.listActiveSessions(
                            user.getId(),
                            tenantId,
                            tokenId
                    );

            System.out.println("[SESSION] 8. Sessoes obtidas: " + (sessions != null ? sessions.size() : "null"));

            return ResponseEntity.ok(
                    sessions != null ? sessions : List.of()
            );

        } catch (Exception e) {

            System.err.println("[SESSION] ❌ ERRO CAPTURADO: " + e.getClass().getSimpleName());
            System.err.println("[SESSION] Mensagem: " + e.getMessage());
            e.printStackTrace();

            return ResponseEntity.ok(List.of());
        }
    }

    @DeleteMapping("/sessions/{sessionId}")
    public ResponseEntity<Void> revokeSession(
            @PathVariable UUID sessionId,
            Authentication authentication,
            HttpServletRequest request
    ) {

        CustomUserDetails user = requireAuthenticatedUser(authentication);

        // Extract tenant from header or JWT
        String tenant = extractTenantFromHeader(request);
        if (tenant == null || tenant.isBlank()) {
            throw new IllegalArgumentException("Tenant identifier is required");
        }
        
        UUID tenantId = resolveTenantId(tenant);

        userSessionService.revokeSpecificSession(
                sessionId,
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
            throw new IllegalArgumentException("Tenant identifier is required");
        }

        UUID tenantId = tenantService.getTenantIdBySchema(tenant);

        if (tenantId == null) {
            throw new IllegalArgumentException("Invalid tenant");
        }

        return tenantId;
    }

    private void createSession(
            UUID userId,
            UUID tenantId,
            JwtToken accessToken,
            HttpServletRequest request
    ) {

        String ipAddress = request.getRemoteAddr();
        String userAgent = request.getHeader("User-Agent");

        if (ipAddress == null || ipAddress.isBlank()) {
            throw new IllegalArgumentException("IP address cannot be null or blank");
        }

        if (userAgent == null || userAgent.isBlank()) {
            throw new IllegalArgumentException("User-Agent cannot be null or blank");
        }

        userSessionService.createSession(
                userId,
                tenantId,
                accessToken.getTokenId(),
                ipAddress,
                userAgent
        );
    }

    private String requireTenant() {
        return TenantContext.getTenant();
    }

    private String extractTenantFromHeader(HttpServletRequest request) {
        String tenant = request.getHeader("X-Tenant-ID");
        
        if (tenant == null || tenant.isBlank()) {
            try {
                tenant = TenantContext.getTenant();
            } catch (IllegalStateException e) {
                // TenantContext não foi setado, tenta recuperar com segurança
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