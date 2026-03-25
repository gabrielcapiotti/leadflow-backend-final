package com.leadflow.backend.multitenancy.resolver;

import com.leadflow.backend.security.jwt.JwtService;
import jakarta.servlet.http.HttpServletRequest;
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
     * Resolve tenant from authorization header (HYBRID APPROACH)
     * 
     * Sources (in order):
     * 1. JWT token (if present and valid) - MANDATORY for authenticated endpoints
     * 2. X-Tenant-Id header (only if no JWT AND endpoint is public)
     * 
     * SECURITY: If JWT present, it MUST match X-Tenant-Id header (if provided)
     * 
     * @param request HttpServletRequest with headers
     * @return tenantSchema (string like "tenant_123" or "public")
     * @throws ResponseStatusException if tenant cannot be resolved or is invalid
     */
    public String resolveTenant(HttpServletRequest request) {

        // Step 1: Try JWT token first (PREFERRED)
        String tenantFromJwt = extractTenantFromJwt(request);
        String tenantFromHeader = request.getHeader("X-Tenant-Id");
        
        if (tenantFromJwt != null && !tenantFromJwt.isBlank()) {
            // JWT token present - USE IT as source of truth
            
            // If header also provided, validate they match (security check)
            if (tenantFromHeader != null && !tenantFromHeader.isBlank()) {
                if (!tenantFromJwt.equalsIgnoreCase(tenantFromHeader)) {
                    throw new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "Tenant mismatch: JWT tenant does not match X-Tenant-Id header"
                    );
                }
            }
            
            return tenantFromJwt;
        }

        // Step 2: Fallback to X-Tenant-Id header (only if no JWT)
        if (tenantFromHeader != null && !tenantFromHeader.isBlank()) {
            return tenantFromHeader;
        }

        // Step 3: Fallback for development environment (ONLY in dev/test)
        if (isDevEnvironment()) {
            return "public";
        }

        // Step 4: No tenant found - error
        throw new ResponseStatusException(
                HttpStatus.UNAUTHORIZED,
                "Missing tenant identification (JWT or X-Tenant-Id header required)"
        );
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
     * Extract tenant from JWT token
     * 
     * @param request HttpServletRequest with Authorization header
     * @return tenant from JWT claims, or null if JWT not found/invalid
     */
    private String extractTenantFromJwt(HttpServletRequest request) {
        String token = extractJwtToken(request);

        if (token == null) {
            return null;
        }

        try {
            return jwtService.extractTenant(token);
        } catch (Exception e) {
            // JWT extraction failed, fall back to header
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