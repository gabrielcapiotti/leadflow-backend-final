package com.leadflow.backend.multitenancy.filter;

import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.multitenancy.resolver.TenantResolver;
import com.leadflow.backend.util.LogSanitizer;

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

        // ✅ Only skip PUBLIC auth endpoints - protected endpoints MUST run TenantFilter
        // 🔥 CRITICAL: /auth/login MUST NOT be ignored - it requires tenant context
        //    Login is tenant-aware in multi-tenant architecture
        boolean isPublicAuth = path.equals("/auth/register")
                || path.equals("/auth/forgot-password")
                || path.equals("/auth/reset-password");
        
        boolean isWebhook = path.startsWith("/stripe/webhook")
                || path.startsWith("/webhooks/")
                || path.startsWith("/webhook/")
                || path.equals("/billing/webhook")
                || path.equals("/billing/checkout")
                || path.equals("/billing/test/get-tenant-id")
                || path.equals("/billing/test/create-stripe-mappings");
        
        boolean isPublicApi = path.startsWith("/public/");

        return isPublicAuth
                || isWebhook
                || isPublicApi
                || path.startsWith("/actuator")
                || path.startsWith("/health")
                || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        logger.debug("TenantFilter executing for path: {}", request.getRequestURI());

        String tenant = null;

        try {

            /* =============================================
               LIMPEZA OBRIGATÓRIA (ThreadLocal reset)
               Cada request começa limpo - threads são reutilizadas!
               ============================================= */

            TenantContext.clear();

            /* =============================================
               RESOLVE TENANT
               ============================================= */

            tenant = tenantResolver.resolveTenant(request);

            if (tenant == null || tenant.isBlank()) {

                logger.warn("Tenant not resolved for path: {}", request.getRequestURI());

                response.sendError(
                        HttpServletResponse.SC_BAD_REQUEST,
                        "Header 'X-Tenant-Id' is required"
                );
                return;
            }

            logger.debug(
                    "Tenant resolved: {}",
                    LogSanitizer.sanitize(tenant)
            );

            /* =============================================
               SET CONTEXT (ÚNICA RESPONSABILIDADE)
               ============================================= */

            TenantContext.setTenant(tenant);
            
            logger.info("🎯 [TENANT-CONTEXT] SET for request: path={}, tenant={}, threadId={}", 
                request.getRequestURI(), 
                LogSanitizer.sanitize(tenant),
                Thread.currentThread().getId());
            
            // ✅ Multi-tenancy is enforced via explicit tenantId parameter in queries
            // ✅ NOT via Hibernate Filter (non-deterministic, session-dependent)
            // ✅ TenantContext is available for audit/logging only

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