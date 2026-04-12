package com.leadflow.backend.multitenancy.filter;

import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.multitenancy.resolver.TenantResolver;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Objects;

public class TenantFilter extends OncePerRequestFilter {

    private static final Logger logger =
            LoggerFactory.getLogger(TenantFilter.class);

    private final TenantResolver tenantResolver;

    public TenantFilter(
            TenantResolver tenantResolver
    ) {
        this.tenantResolver =
                Objects.requireNonNull(
                        tenantResolver,
                        "tenantResolver cannot be null"
                );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getRequestURI();

        // 🔥 NORMALIZA PREFIXO /api
        if (path.startsWith("/api/")) {
            path = path.substring(4);
        }

        // ✅ PUBLIC AUTH ENDPOINTS - no tenant needed yet
        boolean isPublicAuth = path.equals("/auth/register")
                || path.equals("/auth/login")
                || path.equals("/auth/refresh")
                || path.equals("/auth/forgot-password")
                || path.equals("/auth/reset-password");
        
        // ✅ WEBHOOK ENDPOINTS - external, no JWT expected
        boolean isWebhook = path.startsWith("/stripe/webhook")
                || path.startsWith("/webhooks/")
                || path.startsWith("/webhook/")
                || path.equals("/billing/webhook")
                || path.equals("/billing/checkout")
                || path.equals("/billing/test/get-tenant-id")
                || path.equals("/billing/test/create-stripe-mappings");
        
        // ✅ PUBLIC API endpoints
        boolean isPublicApi = path.startsWith("/public/");
        
        // ✅ INFRASTRUCTURE endpoints - internal only
        boolean isInfra = path.startsWith("/actuator")
                || path.startsWith("/health")
                || path.startsWith("/error")  // Error handler (can be called without auth)
                || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs");

        return isPublicAuth || isWebhook || isPublicApi || isInfra;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        logger.debug("TenantFilter executing for path: {}", request.getRequestURI());

        try {

            /* =============================================
               🔐 CRITICAL HOTFIX #5b: Check for JWT FIRST
               If JWT is present, JwtAuthenticationFilter already set TenantContext
               We should NOT clear it!
               ============================================= */
            
            String authHeader = request.getHeader("Authorization");
            boolean hasJwt = authHeader != null && authHeader.startsWith("Bearer ");
            
            if (hasJwt) {
                logger.debug("JWT detected - TenantContext already set by JwtAuthenticationFilter, skipping modifications");
                filterChain.doFilter(request, response);
                return;
            }

            /* =============================================
               LIMPEZA OBRIGATÓRIA NO INÍCIO (ThreadLocal reset)
               Cada request começa limpo - threads são reutilizadas!
               ONLY for non-JWT requests!
               ============================================= */

            com.leadflow.backend.multitenancy.context.TenantContext.clear();

            /* ==============================================
               🔐 CRITICAL FIX #2: Validate authentication BEFORE resolving tenant
               ============================================= */
            
            // If this is a public endpoint, skip tenant resolution
            if (shouldNotFilter(request)) {
                logger.debug("Public endpoint detected, skipping tenant resolution: {}", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }
            
            // Validate authentication exists
            var auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth == null || !auth.isAuthenticated()) {
                logger.debug("No authenticated user for path: {} - letting Spring Security handle", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }
            
            // If NO JWT, check if endpoint requires authentication at all
            if (shouldNotFilter(request)) {
                logger.debug("Public endpoint detected, skipping tenant resolution: {}", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            /* ==============================================
               🔐 CRITICAL FIX #6: Ensure SecurityContext exists BEFORE using TenantContext
               At this point, if we reach here without JWT:
               - Either this is a header-based tenant request (unusual)
               - Or Spring Security will reject it with 401
               ================================================ */
            
            var authentication = SecurityContextHolder.getContext().getAuthentication();
            if (authentication == null || !authentication.isAuthenticated()) {
                logger.debug("No valid authentication for path: {} - letting Spring Security reject", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            /* =============================================
               RESOLVE TENANT (from header only - for backward compat)
               ============================================= */

            java.util.UUID tenant = tenantResolver.resolveTenant(request);

            if (tenant != null) {
                logger.debug("Tenant resolved from header: {}", tenant);
                com.leadflow.backend.multitenancy.context.TenantContext.setTenant(tenant);
                
                logger.info("🎯 [TENANT-CONTEXT] SET for request: path={}, tenant={}, threadId={}", 
                    request.getRequestURI(), 
                    tenant,
                    Thread.currentThread().getId());
            } else {
                logger.debug("No tenant found in header for path: {}", request.getRequestURI());
            }

            filterChain.doFilter(request, response);

        } catch (Exception ex) {

            logger.error("Error in TenantFilter: {}", ex.getMessage(), ex);

            // If it's already a ResponseStatusException, let it through
            if (ex instanceof org.springframework.web.server.ResponseStatusException rse) {
                response.sendError(rse.getStatusCode().value(), rse.getReason());
            } else {
                response.sendError(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Tenant resolution error"
                );
            }

        } finally {
            /* =============================================
               🔐 CRITICAL FIX #5 + HOTFIX #5b: Cleanup ThreadLocal
               
               IMPORTANT: Only clear for non-JWT requests!
               For JWT requests, JwtAuthenticationFilter needs TenantContext downstream
               This finally block runs AFTER filterChain.doFilter() completes
               ============================================= */
            
            String authHeaderCheck = request.getHeader("Authorization");
            boolean hasJwtCheck = authHeaderCheck != null && authHeaderCheck.startsWith("Bearer ");
            
            if (!hasJwtCheck) {
                try {
                    if (TenantContext.getIfPresent() != null) {
                        logger.debug("TenantContext cleanup: Clearing tenant from ThreadLocal (non-JWT request)");
                    }
                    TenantContext.clear();
                } catch (Exception e) {
                    logger.warn("Error clearing TenantContext: {}", e.getMessage());
                }
            } else {
                logger.debug("JWT present - keeping TenantContext for downstream application code");
            }
        }
    }
}