package com.leadflow.backend.security.jwt;

import com.leadflow.backend.multitenancy.context.TenantContext;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.NonNull;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * 🧹 Cleanup filter that ensures TenantContext is cleared after each request
 * 
 * ⚠️ EXECUTION ORDER (critical for correctness):
 * 1. JwtAuthenticationFilter → validates JWT, sets TenantContext + SecurityContext
 * 2. [OTHER FILTERS]
 * 3. [REQUEST PROCESSING] → endpoint uses TenantContext for tenant queries
 * 4. TenantContextCleanupFilter → clears TenantContext (this filter, LAST)
 * 
 * This ensures:
 * ✅ TenantContext is available during entire request processing
 * ✅ No thread-local leakage in servlet container thread pools
 * ✅ Clean state for next request in same thread
 */
public class TenantContextCleanupFilter extends OncePerRequestFilter {

    private static final Logger logger = LoggerFactory.getLogger(TenantContextCleanupFilter.class);

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {
            // 🔥 Let request flow through (all other filters have already executed at this point)
            filterChain.doFilter(request, response);
        } finally {
            // 🧹 CRITICAL: Always clear TenantContext to prevent thread-local leakage
            // This executes AFTER the entire request chain completes
            try {
                TenantContext.clear();
                logger.debug("TenantContext cleared after request completed");
            } catch (Exception ex) {
                logger.warn("Error clearing TenantContext: {}", ex.getMessage());
            }
        }
    }
}
