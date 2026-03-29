package com.leadflow.backend.multitenancy.resolver;

import com.leadflow.backend.security.jwt.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;


/**
 * 🔐 SECURE TENANT RESOLVER
 * 
 * SECURITY PRINCIPLE:
 * - Primary source: JWT token (Authentication header)
 * - Fallback: None (mandatory for authenticated endpoints)
 * - NEVER trust X-Tenant-ID header alone
 *
 * Flow:
 * Authorization: Bearer <token> 
 *   ↓
 * Extract JWT
 *   ↓
 * Get tenantId from JWT claims
 *   ↓
 * Return tenantId (as UUID, not schema name)
 *   ↓
 * CurrentTenantIdentifierResolver validates via DB
 */
@Component
public class TenantResolver {

    private static final Logger logger = LoggerFactory.getLogger(TenantResolver.class);
    private static final String BEARER_PREFIX = "Bearer ";
    private static final String AUTHORIZATION_HEADER = "Authorization";

    private final JwtService jwtService;
    private final Environment environment;

    public TenantResolver(JwtService jwtService, Environment environment) {
        this.jwtService = Objects.requireNonNull(
                jwtService,
                "JwtService must not be null"
        );
        this.environment = Objects.requireNonNull(
                environment,
                "Environment must not be null"
        );
    }

    /**
     * Resolve tenant from authorization header (JWT-AUTHORITATIVE APPROACH)
     * 
     * 🔐 SECURITY PRINCIPLE: JWT is the ONLY authoritative source in authenticated flows
     * 
     * RULE: If JWT exists → ALWAYS use JWT
     * If header diverges → SECURITY THREAT → 401 UNAUTHORIZED
     * 
     * NO FALLBACK to header in authenticated endpoints
     * 
     * @param request HttpServletRequest with headers
     * @return tenantId UUID from JWT (if authenticated)
     * @throws ResponseStatusException if mismatch detected or tenant cannot be resolved
     */
    public java.util.UUID resolveTenant(HttpServletRequest request) {

        // Step 1: Try JWT token (ONLY source of truth for authenticated requests)
        java.util.UUID tenantFromJwt = extractTenantFromJwt(request);
        
        if (tenantFromJwt != null) {
            // ✅ JWT FOUND - validate header STRICTLY
            java.util.UUID tenantFromHeader = extractTenantFromHeader(request);
            
            if (tenantFromHeader != null) {
                // Both JWT and header present - must match exactly
                if (!tenantFromJwt.equals(tenantFromHeader)) {
                    logger.error("SECURITY BREACH ATTEMPT: Tenant mismatch: JWT={} vs HEADER={}", 
                        tenantFromJwt, tenantFromHeader);
                    // BLOCK immediately - this is a potential attack
                    throw new ResponseStatusException(
                            HttpStatus.UNAUTHORIZED,
                            "Tenant mismatch: JWT and header do not match"
                    );
                }
            }
            
            // Always return JWT (it's the sole truth)
            return tenantFromJwt;
        }

        // Step 2: No JWT - check header (for public endpoints only)
        java.util.UUID tenantFromHeader = extractTenantFromHeader(request);
        if (tenantFromHeader != null) {
            // Header only acceptable if no JWT present (public endpoints)
            return tenantFromHeader;
        }

        // Step 3: Fallback for development environment (ONLY in dev/test)
        if (isDevEnvironment()) {
            return java.util.UUID.fromString("00000000-0000-0000-0000-000000000000");
        }

        // Step 4: No tenant found - error
        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Missing tenant identification (JWT or X-Tenant-Id header required)"
        );
    }

    /**
     * Extract tenant from headers with strict UUID validation
     * 
     * Tries multiple header variations:
     * 1. X-Tenant-ID (standard)
     * 2. X-Tenant-Id (common variation)
     * 3. x-tenant-id (lowercase)
     * 
     * @param request HttpServletRequest
     * @return UUID tenant identifier or null if not found/invalid
     */
    private java.util.UUID extractTenantFromHeader(HttpServletRequest request) {
        // Try standard case
        String tenant = request.getHeader("X-Tenant-ID");
        if (tenant != null && !tenant.isBlank()) {
            return parseUUID(tenant);
        }
        
        // Try mixed case
        tenant = request.getHeader("X-Tenant-Id");
        if (tenant != null && !tenant.isBlank()) {
            return parseUUID(tenant);
        }
        
        // Try lowercase
        tenant = request.getHeader("x-tenant-id");
        if (tenant != null && !tenant.isBlank()) {
            return parseUUID(tenant);
        }
        
        return null;
    }

    /**
     * Parse String to UUID with error handling
     */
    private java.util.UUID parseUUID(String value) {
        try {
            return java.util.UUID.fromString(value.trim());
        } catch (IllegalArgumentException e) {
            logger.warn("Invalid UUID format in header: {}", value);
            return null;
        }
    }

    /**
     * Check if we're running in development environment
     * @return true if spring.profiles.active includes dev or test
     */
    private boolean isDevEnvironment() {
        String[] activeProfiles = environment.getActiveProfiles();
        for (String profile : activeProfiles) {
            if (profile.equals("dev") || profile.equals("development") || profile.equals("test")) {
                return true;
            }
        }
        return false;
    }

    /**
     * Extract tenant UUID from JWT token
     * 
     * @param request HttpServletRequest with Authorization header
     * @return tenant UUID from JWT claims, or null if JWT not found/invalid
     */
    private java.util.UUID extractTenantFromJwt(HttpServletRequest request) {
        String token = extractJwtToken(request);

        if (token == null) {
            return null;
        }

        try {
            String tenantStr = jwtService.extractTenant(token);
            return java.util.UUID.fromString(tenantStr);
        } catch (Exception e) {
            // JWT extraction failed, fall back to header
            logger.debug("Failed to extract tenant from JWT: {}", e.getMessage());
            return null;
        }
    }

    /**
     * Extract JWT token from Authorization header
     * 
     * Expected format: "Bearer <token>"
     */
    private String extractJwtToken(HttpServletRequest request) {
        String authHeader = request.getHeader(AUTHORIZATION_HEADER);

        if (authHeader == null || !authHeader.startsWith(BEARER_PREFIX)) {
            return null;
        }

        return authHeader.substring(BEARER_PREFIX.length()).trim();
    }
}