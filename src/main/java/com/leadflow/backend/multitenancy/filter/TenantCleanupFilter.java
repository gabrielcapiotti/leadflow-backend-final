package com.leadflow.backend.multitenancy.filter;

import com.leadflow.backend.multitenancy.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 🔥 CRITICAL FILTER: Guaranteed ThreadLocal cleanup at END of filter chain
 *
 * PURPOSE:
 * - Executes LAST in filter chain (after AuthorizationFilter)
 * - Clears TenantContext + SecurityContext in finally block
 * - Ensures no ThreadLocal leakage in Tomcat thread pool
 *
 * RATIONALE:
 * - JwtFilter sets auth + tenant
 * - AuthorizationFilter uses auth to make access decision
 * - TenantCleanupFilter clears AFTER all auth decisions made
 * - This prevents race conditions and intermittent 401 errors
 *
 * ORDER (registered in SecurityWebConfig):
 * - After: AuthorizationFilter
 * - This is the VERY LAST filter in the chain
 */
@Component
public class TenantCleanupFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TenantCleanupFilter.class);

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // Let the rest of the chain execute (auth, authz, business logic)
            filterChain.doFilter(request, response);
        } finally {
            // 🔥 GUARANTEED: Clean AFTER all auth/authz decisions made
            // This is the ONLY place ThreadLocals are cleared

            try {
                SecurityContextHolder.clearContext();
                logger.debug("✨ CLEANUP: SecurityContext cleared [thread={}]",
                        Thread.currentThread().getName());
            } catch (Exception ex) {
                logger.warn("⚠️ Error clearing SecurityContext: {}", ex.getMessage());
            }

            try {
                TenantContext.clear();
                logger.debug("✨ CLEANUP: TenantContext cleared [thread={}]",
                        Thread.currentThread().getName());
            } catch (Exception ex) {
                logger.warn("⚠️ Error clearing TenantContext: {}", ex.getMessage());
            }
        }
    }
}
