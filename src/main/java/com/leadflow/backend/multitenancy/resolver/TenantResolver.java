package com.leadflow.backend.multitenancy.resolver;

import com.leadflow.backend.security.jwt.JwtService;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
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
     * 🔐 SECURITY PRINCIPLE: JWT is the ONLY source of truth
     * Header-based tenant is completely ignored to prevent switching attacks
     * 
     * @param request HttpServletRequest with headers
     * @return tenantId UUID from JWT, or null if no JWT present
     */
    public java.util.UUID resolveTenant(HttpServletRequest request) {

        // Only source: JWT token in Authorization header
        java.util.UUID tenantFromJwt = extractTenantFromJwt(request);
        
        if (tenantFromJwt != null) {
            return tenantFromJwt;
        }

        // No JWT found - return null (let Spring Security handle authorization)
        // ✅ Do NOT throw exception here - Spring Security will bar unauthenticated requests
        logger.debug("No JWT found in request - TenantResolver returning null (Spring Security will handle authorization)");
        return null;
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
            // JWT extraction failed - cannot resolve tenant
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