package com.leadflow.backend.multitenancy.filter;

import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.multitenancy.resolver.TenantResolver;
import com.leadflow.backend.security.tenant.HibernateFilterService;
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
    private final HibernateFilterService hibernateFilterService;

    public TenantFilter(
            TenantResolver tenantResolver,
            HibernateFilterService hibernateFilterService
    ) {
        this.tenantResolver =
                Objects.requireNonNull(
                        tenantResolver,
                        "tenantResolver cannot be null"
                );
        this.hibernateFilterService =
                Objects.requireNonNull(
                        hibernateFilterService,
                        "hibernateFilterService cannot be null"
                );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getRequestURI();

        boolean isPublicAuth = path.startsWith("/auth/");
        boolean isWebhook = path.startsWith("/stripe/webhook")
                || path.startsWith("/webhooks/")
                || path.startsWith("/webhook/");
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
        boolean hibernateFilterEnabled = false;

        try {

            /* =============================================
               VERIFICA SE JÁ EXISTE TENANT
               ============================================= */

            String existingTenant = TenantContext.getIfPresent();

            if (existingTenant != null && !existingTenant.isBlank()) {

                logger.debug(
                        "Tenant already present: {}",
                        LogSanitizer.sanitize(existingTenant)
                );

                filterChain.doFilter(request, response);
                return;
            }

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
            
            // Enable Hibernate filter to enforce multi-tenant isolation
            hibernateFilterService.enableTenantFilter(tenant);
            hibernateFilterEnabled = true;

            // ✅ ESSENTIAL: FilterChain must execute with TenantContext active
            filterChain.doFilter(request, response);

        } catch (Exception ex) {

            logger.error("Error resolving tenant", ex);

            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Error resolving tenant"
            );

        } finally {

            /* =============================================
               CLEANUP (ESSENTIAL: Keep context for other filters!)
               The TenantContext must remain active for the entire filter chain.
               This is cleaned up by Spring's RequestContextListener AFTER
               doFilter() returns, ensuring Hibernate Filter works correctly.
               ============================================= */
            
            // ❌ DO NOT disable Hibernate filter here!
            // The filter must remain active for JwtAuthenticationFilter and beyond
            // hibernateFilterService.disableTenantFilter();
            
            // ❌ DO NOT clear TenantContext here!
            // TenantContext.clear();
        }
    }
}