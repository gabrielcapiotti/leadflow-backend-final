package com.leadflow.backend.security.jwt;

import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.multitenancy.service.TenantService;
import com.leadflow.backend.security.CustomUserDetails;
import com.leadflow.backend.service.auth.UserSessionService;
import com.leadflow.backend.util.LogSanitizer;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.Objects;
import java.util.UUID;

public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final Logger logger =
            LoggerFactory.getLogger(JwtAuthenticationFilter.class);

    private final JwtService jwtService;
    private final UserDetailsService userDetailsService;
    private final UserSessionService userSessionService;
    private final TenantService tenantService;

    public JwtAuthenticationFilter(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            UserSessionService userSessionService,
            TenantService tenantService
    ) {
        this.jwtService = Objects.requireNonNull(jwtService);
        this.userDetailsService = Objects.requireNonNull(userDetailsService);
        this.userSessionService = Objects.requireNonNull(userSessionService);
        this.tenantService = Objects.requireNonNull(tenantService);
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {

        String path = request.getRequestURI();

                if (path.startsWith("/api/")) {
                        path = path.substring(4);
                }

        boolean skip =
                path.equals("/auth/register")
                        || path.equals("/auth/login")
                        || path.equals("/auth/refresh")
                        || path.equals("/auth/debug")
                        || path.startsWith("/actuator")
                        || path.startsWith("/swagger")
                        || path.startsWith("/v3/api-docs")
                        || path.startsWith("/public/")
                        || path.startsWith("/webhooks")
                        || path.startsWith("/billing/checkout")
                        || path.startsWith("/billing/webhook")
                        || path.startsWith("/stripe/webhook")
                        || path.startsWith("/payments/webhook");

        return skip;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {

        try {

            String token = extractToken(request);

            if (token == null) {
                filterChain.doFilter(request, response);
                return;
            }

            if (SecurityContextHolder.getContext().getAuthentication() != null) {
                filterChain.doFilter(request, response);
                return;
            }

            String email = jwtService.extractEmail(token);

            if (email == null) {
                filterChain.doFilter(request, response);
                return;
            }

            UserDetails userDetails =
                    userDetailsService.loadUserByUsername(email);

            if (!(userDetails instanceof CustomUserDetails customUser)) {
                filterChain.doFilter(request, response);
                return;
            }

            UUID userId = customUser.getId();

            /* =====================================================
               TENANT RESOLUTION (deterministic order)
               ===================================================== */

            // Priority 1: Extract from JWT token (source of truth)
            String tenant = jwtService.extractTenant(token);

            // Priority 2: Fallback to X-Tenant-ID header
            if (tenant == null || tenant.isBlank()) {
                tenant = request.getHeader("X-Tenant-ID");
            }

            // Priority 3: Check if already set in context (should be set by TenantFilter)
            if (tenant == null || tenant.isBlank()) {
                try {
                    tenant = TenantContext.getTenant();
                } catch (Exception ex) {
                    logger.debug("TenantContext not initialized: {}", ex.getMessage());
                }
            }

            if (tenant == null || tenant.isBlank()) {

                logger.warn(
                        "Tenant could not be resolved for request {}",
                        request.getRequestURI()
                );

                response.sendError(
                        HttpServletResponse.SC_UNAUTHORIZED,
                        "Tenant not resolved"
                );
                return;
            }

            TenantContext.setTenant(tenant);

            logger.debug(
                    "Tenant resolved: {}",
                    LogSanitizer.sanitize(tenant)
            );

            /* =====================================================
               TOKEN VALIDATION
               ===================================================== */

            boolean baseValid =
                    jwtService.isTokenValid(token, userDetails, userId, tenant);

            if (!baseValid) {
                filterChain.doFilter(request, response);
                return;
            }

            if (!isTokenStillValidAfterPasswordChange(token, customUser)) {
                filterChain.doFilter(request, response);
                return;
            }

            /* =====================================================
               SESSION TRACKING
               ===================================================== */

            String tokenId = jwtService.extractTokenId(token);

            UUID tenantId =
                    tenantService.getTenantIdBySchema(tenant);

            userSessionService.processSessionActivity(
                    tokenId,
                    tenantId,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent")
            );

            /* =====================================================
               AUTHENTICATION
               ===================================================== */

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            authToken.setDetails(
                    new WebAuthenticationDetailsSource()
                            .buildDetails(request)
            );

            SecurityContextHolder
                    .getContext()
                    .setAuthentication(authToken);

        } catch (Exception ex) {

            logger.debug(
                    "JWT authentication failed: {}",
                    LogSanitizer.sanitize(ex.getMessage())
            );

        }

        filterChain.doFilter(request, response);
    }

    private boolean isTokenStillValidAfterPasswordChange(
            String token,
            CustomUserDetails userDetails
    ) {

        Date issuedAt = jwtService.extractIssuedAt(token);

        if (issuedAt == null) {
            return false;
        }

        LocalDateTime tokenIssuedAt =
                issuedAt.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                        .truncatedTo(ChronoUnit.SECONDS);

        LocalDateTime credentialsUpdatedAt =
                userDetails.getCredentialsUpdatedAt();

        if (credentialsUpdatedAt == null) {
            return true;
        }

        LocalDateTime normalizedCredentialsUpdatedAt =
                credentialsUpdatedAt.truncatedTo(ChronoUnit.SECONDS);

        boolean withinGracePeriod =
                normalizedCredentialsUpdatedAt.isBefore(
                        tokenIssuedAt.plusSeconds(30)
                );

        return !tokenIssuedAt.isBefore(normalizedCredentialsUpdatedAt)
                || withinGracePeriod;
    }

    private String extractToken(HttpServletRequest request) {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        return authHeader.substring(7);
    }
}
