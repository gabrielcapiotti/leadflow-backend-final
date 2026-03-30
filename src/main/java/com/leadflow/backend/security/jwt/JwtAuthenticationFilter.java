package com.leadflow.backend.security.jwt;

import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.multitenancy.service.TenantService;
import com.leadflow.backend.security.CustomUserDetails;
import com.leadflow.backend.security.exception.UnauthorizedException;
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
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {

        String path = request.getRequestURI();

        if (path.startsWith("/api/")) {
            path = path.substring(4);
        }

        // ✅ Only skip PUBLIC auth endpoints (no token needed)
        return path.equals("/auth/register")
                || path.equals("/auth/login")
                || path.equals("/auth/refresh")
                || path.equals("/auth/forgot-password")
                || path.equals("/auth/reset-password")
                || path.startsWith("/actuator")
                || path.startsWith("/swagger")
                || path.startsWith("/v3/api-docs")
                || path.startsWith("/public/")
                || path.startsWith("/webhooks")
                || path.equals("/billing/checkout")
                || path.equals("/billing/checkout-session")
                || path.equals("/billing/webhook")
                || path.equals("/stripe/webhook");
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
               TENANT CONTEXT SETUP (FROM JWT - AUTHORITATIVE SOURCE)
               🔒 JWT is the sole source of truth for tenant
               ===================================================== */
            String tenant = jwtService.extractTenant(token);
            
            if (tenant == null || tenant.isBlank()) {
                logger.warn("Tenant missing in JWT for {}", request.getRequestURI());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }

            // ✅ CRITICAL VALIDATION: Verify tenant format before UUID conversion
            if (!tenant.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
                logger.error("CRITICAL: Invalid UUID format in JWT tenant after extraction: {}", LogSanitizer.sanitize(tenant));
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid tenant format in token");
                return;
            }

            UUID tenantId;
            try {
                tenantId = UUID.fromString(tenant);
                // ✅ VALIDATION: Verify UUID was correctly reconstructed from String
                String reconstructed = tenantId.toString();
                if (!reconstructed.equals(tenant)) {
                    logger.error("CRITICAL: UUID mismatch after conversion! Original: {} | Reconstructed: {}", 
                        LogSanitizer.sanitize(tenant), LogSanitizer.sanitize(reconstructed));
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Tenant format validation failed");
                    return;
                }
            } catch (IllegalArgumentException e) {
                logger.error("CRITICAL: Failed to parse UUID from JWT tenant: {} | Error: {}", 
                    LogSanitizer.sanitize(tenant), e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid tenant format");
                return;
            }

            // 🔒 FORCE JWT tenant as authoritative - overwrites any header-based value
            // This prevents header-based tenant switching attacks
            // ✅ By now, tenantId has been triple-validated: format check, UUID.fromString(), roundtrip test
            TenantContext.setTenant(tenantId);
            
            logger.debug(
                    "🔒 AUTH CONTEXT SET (from JWT) | email={} | tenant={}",
                    LogSanitizer.sanitize(email),
                    tenantId.toString()
            );

            /* =====================================================
               🔒 SECURITY: DETECT HEADER MISMATCH ATTACK
               If X-Tenant-ID header present and differs from JWT tenant,
               reject with 403 Forbidden (explicit header switch attempt)
               ===================================================== */
            String headerTenant = request.getHeader("X-Tenant-ID");
            if (headerTenant != null && !headerTenant.isBlank()) {
                // Header present - validate it matches JWT tenant
                if (!headerTenant.equalsIgnoreCase(tenant)) {
                    logger.warn(
                            "🔒 SECURITY: Tenant mismatch attack detected | JWT tenant: {} | header tenant: {} | email: {}",
                            LogSanitizer.sanitize(tenant),
                            LogSanitizer.sanitize(headerTenant),
                            LogSanitizer.sanitize(email)
                    );
                    response.sendError(HttpServletResponse.SC_FORBIDDEN, "Tenant mismatch: header does not match JWT");
                    return;
                }
            }

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
               SESSION TRACKING (NON-BLOCKING)
               ===================================================== */

            String tokenId = jwtService.extractTokenId(token);

            try {
                userSessionService.processSessionActivity(
                        tokenId,
                        tenantId,
                        request.getRemoteAddr(),
                        request.getHeader("User-Agent")
                );
            } catch (UnauthorizedException ex) {
                // 🔒 Session validation FAILED - must block
                logger.warn(
                        "Session validation failed (BLOCKING): {} | email={}",
                        LogSanitizer.sanitize(ex.getMessage()),
                        LogSanitizer.sanitize(email)
                );
                // Invalid session = invalid JWT, must reject
                throw ex;
            }

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
        } finally {
            // 🔴 CRITICAL: Ensure filterChain is called ALWAYS
            // This prevents exceptions from blocking downstream filters
            // Note: TenantFilter.finally() will clean up TenantContext for next request
            try {
                filterChain.doFilter(request, response);
            } catch (Exception ex) {
                logger.error("Error in downstream filter chain: {}", ex.getMessage());
            }
        }
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