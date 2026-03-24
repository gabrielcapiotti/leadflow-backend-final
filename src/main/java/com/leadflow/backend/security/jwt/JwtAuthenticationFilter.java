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
import java.time.*;
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

        return path.startsWith("/auth/")
                || path.startsWith("/actuator")
                || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/public/")
                || path.startsWith("/webhooks")
                || path.startsWith("/billing")
                || path.startsWith("/stripe");
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

            /* =====================================================
               TENANT CONTEXT SETUP (EARLY)
               ===================================================== */
            String tenant = jwtService.extractTenant(token);
            
            if (tenant == null || tenant.isBlank()) {
                logger.warn("Tenant missing in JWT for {}", request.getRequestURI());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }

            // ✅ Validate JWT tenant matches request context tenant (set by TenantFilter)
            String tenantFromContext = TenantContext.getTenant();

            if (!tenant.equals(tenantFromContext)) {
                logger.warn(
                        "JWT tenant mismatch: JWT={} | Context={} | path={}",
                        LogSanitizer.sanitize(tenant),
                        LogSanitizer.sanitize(tenantFromContext),
                        request.getRequestURI()
                );
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid tenant");
                return;
            }

            logger.debug(
                    "AUTH CONTEXT | email={} | tenant={}",
                    LogSanitizer.sanitize(email),
                    LogSanitizer.sanitize(tenant)
            );

            /* =====================================================
               LOAD USER (WITH CONTEXT)
               ===================================================== */

            UserDetails userDetails =
                    userDetailsService.loadUserByUsername(email);

            if (!(userDetails instanceof CustomUserDetails customUser)) {
                filterChain.doFilter(request, response);
                return;
            }

            UUID userId = customUser.getId();

            /* =====================================================
               TOKEN VALIDATION (WITH TENANT)
               ===================================================== */

            if (!jwtService.isTokenValid(token, userDetails, userId, tenant)) {
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
            UUID tenantId = tenantService.getTenantIdBySchema(tenant);

            userSessionService.processSessionActivity(
                    tokenId,
                    tenantId,
                    request.getRemoteAddr(),
                    request.getHeader("User-Agent")
            );

            UsernamePasswordAuthenticationToken authToken =
                    new UsernamePasswordAuthenticationToken(
                            userDetails,
                            null,
                            userDetails.getAuthorities()
                    );

            authToken.setDetails(
                    new WebAuthenticationDetailsSource().buildDetails(request)
            );

            SecurityContextHolder.getContext().setAuthentication(authToken);

        } catch (Exception ex) {

            logger.error(
                    "Unexpected JWT authentication error: {}",
                    LogSanitizer.sanitize(ex.getMessage())
            );
        }

        filterChain.doFilter(request, response);
    }

    private String extractToken(HttpServletRequest request) {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return null;
        }

        return authHeader.substring(7);
    }

    private boolean isTokenStillValidAfterPasswordChange(
            String token,
            CustomUserDetails userDetails
    ) {

        Date issuedAt = jwtService.extractIssuedAt(token);
        if (issuedAt == null) return false;

        LocalDateTime tokenIssuedAt =
                issuedAt.toInstant()
                        .atZone(ZoneId.systemDefault())
                        .toLocalDateTime()
                        .truncatedTo(ChronoUnit.SECONDS);

        LocalDateTime credentialsUpdatedAt =
                userDetails.getCredentialsUpdatedAt();

        if (credentialsUpdatedAt == null) return true;

        LocalDateTime normalized =
                credentialsUpdatedAt.truncatedTo(ChronoUnit.SECONDS);

        boolean grace =
                normalized.isBefore(tokenIssuedAt.plusSeconds(30));

        return !tokenIssuedAt.isBefore(normalized) || grace;
    }
}