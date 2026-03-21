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

    public TenantFilter(TenantResolver tenantResolver) {
        this.tenantResolver =
                Objects.requireNonNull(
                        tenantResolver,
                        "TenantResolver must not be null"
                );
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getRequestURI();
        
        // Remove query parameters if present
        if (path.contains("?")) {
            path = path.substring(0, path.indexOf("?"));
        }

        // Public auth endpoints (NO /api/ prefix - rotas públicas não têm /api/)
        boolean isPublicAuth = path.startsWith("/auth/register")
                || path.startsWith("/auth/login")
                || path.startsWith("/auth/refresh")
                || path.startsWith("/auth/debug");
        
        // Public API endpoints (no authentication or tenant required)
        boolean isPublicApi = path.startsWith("/public/");
        
        // Admin endpoints (global/system-wide, no tenant required)
        boolean isAdminEndpoint = path.startsWith("/admin/");
        
        return isPublicAuth
                || isPublicApi
                || isAdminEndpoint
                || path.startsWith("/actuator")
                || path.startsWith("/health")
                || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs");
    }

    @Override
    protected boolean shouldNotFilterAsyncDispatch() {
        return true;
    }

    @Override
    protected boolean shouldNotFilterErrorDispatch() {
        return true;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        boolean tenantSetByThisFilter = false;

        try {

            /* =============================================
               VERIFICA SE TENANT JÁ EXISTE NO CONTEXTO
               ============================================= */

            String existingTenant = null;

            try {
                existingTenant = TenantContext.getTenant();
            } catch (IllegalStateException ignored) {
                // tenant ainda não definido
            }

            if (existingTenant != null && !existingTenant.isBlank()) {

                logger.debug(
                        "Tenant already present in context: {}",
                        LogSanitizer.sanitize(existingTenant)
                );

                filterChain.doFilter(request, response);
                return;
            }

            /* =============================================
               RESOLVE TENANT DO HEADER
               ============================================= */

            String tenant = tenantResolver.resolveTenant(request);

            if (tenant == null || tenant.isBlank()) {

                logger.warn(
                        "Tenant could not be resolved for request {}",
                        request.getRequestURI()
                );

                response.sendError(
                        HttpServletResponse.SC_BAD_REQUEST,
                        "Header 'X-Tenant-ID' é obrigatório"
                );

                return;
            }

            logger.debug(
                    "Tenant resolved: {}",
                    LogSanitizer.sanitize(tenant)
            );

            TenantContext.setTenant(tenant);
            tenantSetByThisFilter = true;

            filterChain.doFilter(request, response);

        } catch (IllegalArgumentException ex) {

            logger.warn(
                    "Invalid tenant header: {}",
                    LogSanitizer.sanitize(ex.getMessage())
            );

            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    ex.getMessage()
            );

        } catch (Exception ex) {

            logger.error(
                    "Unexpected error resolving tenant",
                    ex
            );

            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Erro ao resolver tenant"
            );

        } finally {

            if (tenantSetByThisFilter) {
                TenantContext.clear();
            }
        }
    }
}
