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

    private static final Logger logger = LoggerFactory.getLogger(TenantFilter.class);
    private final TenantResolver tenantResolver;

    public TenantFilter(TenantResolver tenantResolver) {
        this.tenantResolver =
                Objects.requireNonNull(tenantResolver, "TenantResolver must not be null");
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getRequestURI();

        return path.equals("/auth/register")
            || path.equals("/auth/login")
            || path.equals("/auth/refresh")
            || path.startsWith("/api/auth")
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

        try {

            String tenant = tenantResolver.resolveTenant(request);

            if (tenant == null || tenant.isBlank()) {

                response.sendError(
                        HttpServletResponse.SC_BAD_REQUEST,
                        "Header 'X-Tenant-ID' é obrigatório"
                );
                return;
            }

            TenantContext.setTenant(tenant);

            filterChain.doFilter(request, response);

        } catch (IllegalArgumentException ex) {

            response.sendError(
                    HttpServletResponse.SC_BAD_REQUEST,
                    ex.getMessage()
            );

        } catch (Exception ex) {

            response.sendError(
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Erro ao resolver tenant"
            );

        } finally {

            // Evita vazamento de tenant entre threads
            TenantContext.clear();
        }
    }
}