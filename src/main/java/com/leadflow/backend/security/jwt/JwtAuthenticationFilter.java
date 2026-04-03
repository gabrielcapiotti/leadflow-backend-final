package com.leadflow.backend.security.jwt;

import com.leadflow.backend.config.converter.SafeUUIDDeserializer;
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

            String email;
            try {
                email = jwtService.extractEmail(token);
            } catch (Exception ex) {
                logger.debug("Failed to extract email from token: {}", ex.getMessage());
                filterChain.doFilter(request, response);
                return;
            }

            if (email == null) {
                filterChain.doFilter(request, response);
                return;
            }

            /* =====================================================
               TENANT CONTEXT SETUP (FROM JWT - AUTHORITATIVE SOURCE)
               🔒 JWT is the sole source of truth for tenant
               ===================================================== */
            String tenant;
            try {
                tenant = jwtService.extractTenant(token);
            } catch (Exception ex) {
                logger.debug("Failed to extract tenant from token: {}", ex.getMessage());
                filterChain.doFilter(request, response);
                return;
            }
            
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
                // Use safe UUID deserialization with corruption detection
                tenantId = SafeUUIDDeserializer.deserialize(tenant);
                
                // DOUBLE-CHECK: Verify roundtrip after conversion
                String tenantRoundtrip = tenantId.toString();
                if (!tenantRoundtrip.equals(tenant)) {
                    logger.error("CRITICAL: Tenant UUID roundtrip failed after conversion | Original: {} | Roundtrip: {}", 
                        LogSanitizer.sanitize(tenant), LogSanitizer.sanitize(tenantRoundtrip));
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Tenant validation failed");
                    return;
                }
                
                logger.debug("✅ Tenant UUID validated and converted successfully: {}", tenantId);
            } catch (IllegalArgumentException e) {
                logger.error("CRITICAL: Failed to parse UUID from JWT tenant (corruption detected?): {} | Error: {}", 
                    LogSanitizer.sanitize(tenant), e.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid tenant format");
                return;
            }

            // 🔒 FORCE JWT tenant as authoritative - header is completely ignored
            // This prevents header-based tenant switching attacks
            // ✅ By now, tenantId has been triple-validated: format check, UUID.fromString(), roundtrip test
            TenantContext.setTenant(tenantId);
            
            logger.debug(
                    "🔒 AUTH CONTEXT SET (from JWT only) | email={} | tenant={}",
                    LogSanitizer.sanitize(email),
                    tenantId.toString()
            );

            /* =====================================================
               ✅ NOTE: X-Tenant-ID header is completely ignored
               JWT is the sole source of truth for tenant identity
               Any header-based tenant values are silently discarded
               ===================================================== */

            /* =====================================================
               LOAD USER (WITH CONTEXT)
               ===================================================== */

            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(email);
            } catch (Exception ex) {
                logger.debug("User not found or error loading user details: {}", ex.getMessage());
                filterChain.doFilter(request, response);
                return;
            }

            if (!(userDetails instanceof CustomUserDetails customUser)) {
                filterChain.doFilter(request, response);
                return;
            }

            UUID userId = customUser.getId();

            /* =====================================================
               TOKEN VALIDATION (WITH TENANT)
               ===================================================== */

            boolean tokenValid;
            try {
                tokenValid = jwtService.isTokenValid(token, userDetails, userId, tenant);
            } catch (Exception ex) {
                logger.debug("Token validation failed: {}", ex.getMessage());
                filterChain.doFilter(request, response);
                return;
            }

            if (!tokenValid) {
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

            String tokenId;
            try {
                tokenId = jwtService.extractTokenId(token);
            } catch (Exception ex) {
                logger.debug("Failed to extract token ID: {}", ex.getMessage());
                filterChain.doFilter(request, response);
                return;
            }

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
            
            // ✅ Call filterChain EXACTLY ONCE (OncePerRequestFilter contract)
            filterChain.doFilter(request, response);

        } catch (Exception ex) {

            logger.error(
                    "Unexpected authentication error: {}",
                    LogSanitizer.sanitize(ex.getMessage())
            );
            
            // ✅ Even on exception, filterChain must be called once
            try {
                filterChain.doFilter(request, response);
            } catch (Exception chainEx) {
                logger.error("Error in downstream filter chain: {}", chainEx.getMessage());
            }
        }
    }

    private String extractToken(HttpServletRequest request) {

        String authHeader = request.getHeader("Authorization");

        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }

        if (!authHeader.startsWith("Bearer ")) {
            logger.debug("Invalid Authorization header format: missing 'Bearer ' prefix");
            return null;
        }

        String token = authHeader.substring(7).trim();

        // ✅ CRITICAL: Reject empty or blank token
        if (token.isBlank()) {
            logger.debug("Authorization header present but token is blank");
            return null;
        }

        // ✅ CRITICAL: Basic structural validation
        // JWT format: xxxxx.yyyyy.zzzzz (3 parts separated by dots)
        if (!token.contains(".") || token.split("\\.").length != 3) {
            logger.warn("Invalid token structure: expected JWT format (3 parts separated by dots)");
            return null;
        }

        // ✅ CRITICAL: Detect obvious corruption (control characters, invalid UTF-8 sequences)
        // Check for suspicious byte sequences like BOM or binary data
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            // JWT should only contain Base64URL chars: A-Z, a-z, 0-9, -, _, .
            // Reject if we see control characters (< 32), high bytes (> 127), or other invalid chars
            if (c < 32 || (c > 127 && c != '.')) {
                logger.warn("Invalid token: contains suspicious characters (possible corruption/encoding issue)");
                return null;
            }
        }

        return token;
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