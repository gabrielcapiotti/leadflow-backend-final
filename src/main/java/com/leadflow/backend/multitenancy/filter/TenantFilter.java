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

        java.util.UUID tenant = null;

        try {

            /* =============================================
               LIMPEZA OBRIGATÓRIA (ThreadLocal reset)
               Cada request começa limpo - threads são reutilizadas!
               ============================================= */

            com.leadflow.backend.multitenancy.context.TenantContext.clear();

            /* =============================================
               🔒 SECURITY: If JWT present, skip tenant resolution from header
               JWT will be the sole source of truth (set by JwtAuthenticationFilter)
               ============================================= */
            
            String authHeader = request.getHeader("Authorization");
            boolean hasJwt = authHeader != null && authHeader.startsWith("Bearer ");
            
            if (hasJwt) {
                // 🔒 Skip header-based tenant resolution when JWT is present
                // JwtAuthenticationFilter will set the tenant from JWT (authoritative source)
                logger.debug("JWT detected - skipping header-based tenant resolution, deferring to JwtAuthenticationFilter");
                filterChain.doFilter(request, response);
                return;
            }

            /* =============================================
               RESOLVE TENANT (only for non-JWT requests)
               ============================================= */

            tenant = tenantResolver.resolveTenant(request);

            if (tenant == null) {
                // ✅ No tenant found (and no JWT)
                // Let Spring Security decide if request is authorized
                // For public endpoints: continue
                // For protected endpoints: Spring Security will bar with 401
                logger.debug("No tenant resolved for path: {} (unauthenticated request - letting Spring Security handle)", request.getRequestURI());
                filterChain.doFilter(request, response);
                return;
            }

            logger.debug(
                    "Tenant resolved from header: {}",
                    tenant
            );

            /* =============================================
               SET CONTEXT (ÚNICA RESPONSABILIDADE)
               ============================================= */

            com.leadflow.backend.multitenancy.context.TenantContext.setTenant(tenant);
            
            logger.info("🎯 [TENANT-CONTEXT] SET for request: path={}, tenant={}, threadId={}", 
                request.getRequestURI(), 
                tenant,
                Thread.currentThread().getId());

            filterChain.doFilter(request, response);

        } catch (Exception ex) {

            logger.error("Error resolving tenant", ex);

            // If it's already a ResponseStatusException, let it through (e.g., tenant mismatch = 403)
            if (ex instanceof org.springframework.web.server.ResponseStatusException rse) {
                response.sendError(rse.getStatusCode().value(), rse.getReason());
            } else {
                response.sendError(
                        HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                        "Error resolving tenant"
                );
            }

        } finally {

            /* =============================================
               LIMPEZA CRÍTICA (ThreadLocal cleanup)
               Garante que próximas requests não reutilizam contexto
               ============================================= */
            
            TenantContext.clear();
            
            logger.debug("TenantFilter cleanup: TenantContext cleared");
        }
    }
}