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

        String requestId = request.getRequestURI() + "@" + Thread.currentThread().getId();
        logger.info("🔐 JWT_FILTER_START: {} [thread={}]", request.getRequestURI(), Thread.currentThread().getName());

        try {
            // ✅ STEP 1: Extract and validate token without any context setup yet
            String token = extractToken(request);

            if (token == null) {
                // ✅ CRITICAL FIX #7: For protected endpoints (not in shouldNotFilter),
                // throw exception to trigger 401, don't just pass to next filter
                // This prevents AnonymousAuthenticationToken from being created
                logger.warn("🔐 Protected endpoint requires authentication token: {}", request.getRequestURI());
                throw new UnauthorizedException("Authentication token required");
            }

            // ✅ STEP 3: Extract email (no context needed yet)
            String email;
            try {
                email = jwtService.extractEmail(token);
            } catch (Exception ex) {
                logger.warn("Failed to extract email from token: {}", ex.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token: email extraction failed");
                return;
            }

            if (email == null) {
                logger.warn("Email claim not found in JWT token");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token: email claim missing");
                return;
            }

            // ✅ STEP 4: Extract tenant STRING (validate format, NOT convert to UUID yet)
            String tenant;
            try {
                tenant = jwtService.extractTenant(token);
            } catch (Exception ex) {
                logger.warn("Failed to extract tenant from token: {}", ex.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token: tenant extraction failed");
                return;
            }
            
            if (tenant == null || tenant.isBlank()) {
                logger.warn("Tenant claim missing or empty in JWT");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token: tenant claim missing");
                return;
            }

            // ✅ STEP 5: Validate tenant UUID format BEFORE any context setup
            if (!tenant.matches("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")) {
                logger.error("CRITICAL: Invalid UUID format in JWT tenant");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid tenant format in token");
                return;
            }

            UUID tenantId;
            try {
                tenantId = SafeUUIDDeserializer.deserialize(tenant);
                
                // Verify roundtrip integrity
                if (!tenantId.toString().equals(tenant)) {
                    logger.error("CRITICAL: Tenant UUID roundtrip integrity failed");
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Tenant validation failed");
                    return;
                }
            } catch (IllegalArgumentException e) {
                logger.error("CRITICAL: Failed to parse UUID from JWT tenant");
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid tenant format");
                return;
            }

            // ✅ STEP 6: SET TENANT CONTEXT FIRST (CRITICAL for multi-tenant UserDetailsService)
            // UserDetailsService queries depend on TenantContext being set
            TenantContext.setTenant(tenantId);
            logger.debug("TenantContext set for user loading: tenant={}", tenantId);

            // ✅ STEP 7: Load user (NOW with TenantContext set for Hibernate filtering)
            UserDetails userDetails;
            try {
                userDetails = userDetailsService.loadUserByUsername(email);
            } catch (Exception ex) {
                logger.warn("User not found or error loading user details for email: {}", 
                    email);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "User not found");
                return;
            }

            if (!(userDetails instanceof CustomUserDetails customUser)) {
                logger.warn("UserDetails not CustomUserDetails instance for email: {}", email);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid user details");
                return;
            }

            UUID userId = customUser.getId();

            // ✅ STEP 7: Validate token (BEFORE setting any context)
            boolean tokenValid;
            try {
                tokenValid = jwtService.isTokenValid(token, userDetails, userId, tenant);
            } catch (Exception ex) {
                logger.warn("Token validation failed: {}", ex.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token");
                return;
            }

            if (!tokenValid) {
                logger.warn("Token validation returned false for user: {}", email);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid or expired token");
                return;
            }

            // ✅ STEP 8: Check password change validity (BEFORE setting context)
            if (!isTokenStillValidAfterPasswordChange(token, customUser)) {
                logger.warn("Token invalidated due to password change for user: {}", email);
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Token invalidated by password change");
                return;
            }

            // ✅ STEP 9: Extract token ID (BEFORE session validation)
            String tokenId;
            try {
                tokenId = jwtService.extractTokenId(token);
            } catch (Exception ex) {
                logger.warn("Failed to extract token ID: {}", ex.getMessage());
                response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Invalid token: missing token ID");
                return;
            }

            // ✅ STEP 10: Validate session (NON-BLOCKING - don't fail auth if session issues exist)
            // Session tracking is for audit/security, NOT for auth enforcement
            try {
                userSessionService.processSessionActivity(
                        tokenId,
                        tenantId,
                        request.getRemoteAddr(),
                        request.getHeader("User-Agent")
                );
            } catch (UnauthorizedException ex) {
                logger.warn("Session validation warning (non-blocking): {}", ex.getMessage());
                // Continue - valid JWT means valid auth, even if session has issues
            } catch (Exception ex) {
                logger.warn("Session tracking failed (non-blocking): {}", ex.getMessage());
                // Continue - session tracking failure doesn't block authentication
            }

            // ✅ STEP 11: Set authentication in SecurityContext
            // (TenantContext already set in STEP 6 before loadUser)
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
            logger.info("✅ JWT_FILTER_SUCCESS: Auth set for email={}, tenant={}, thread={}", 
                        email, tenantId, Thread.currentThread().getName());
            
            // ✅ STEP 12: Call filterChain (TenantContext + SecurityContext fully initialized)
            filterChain.doFilter(request, response);

        } catch (UnauthorizedException ex) {
            // ✅ CRITICAL: Reject auth cleanly
            logger.warn("Authentication rejected: {}", ex.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        } catch (Exception ex) {
            // ✅ CRITICAL: Unexpected error - reject cleanly
            logger.error("Unexpected authentication error: {} → {}",
                    ex.getClass().getSimpleName(),
                    ex.getMessage());
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
            return;
        }
    }

    private String extractToken(HttpServletRequest request) throws UnauthorizedException {

        String authHeader = request.getHeader("Authorization");

        // ✅ Case 1: NO Authorization header → return null (will continue to next filter)
        if (authHeader == null || authHeader.isBlank()) {
            return null;
        }

        // ✅ Case 2 & 3: Authorization header EXISTS → MUST be valid
        // If header exists but is malformed, throw exception (trigger 401)
        
        if (!authHeader.startsWith("Bearer ")) {
            logger.warn("Invalid Authorization header format: missing 'Bearer ' prefix");
            throw new UnauthorizedException("Authorization header must start with 'Bearer '");
        }

        String token = authHeader.substring(7).trim();

        // ✅ CRITICAL: Token present but empty → invalid
        if (token.isBlank()) {
            logger.warn("Authorization header present but token is blank");
            throw new UnauthorizedException("Token cannot be empty");
        }

        // ✅ CRITICAL: Token structure validation - must be 3 parts separated by dots
        // JWT format: xxxxx.yyyyy.zzzzz
        if (!token.contains(".") || token.split("\\.").length != 3) {
            logger.warn("Invalid token structure: expected JWT format (3 parts separated by dots)");
            throw new UnauthorizedException("Token must be valid JWT format (3 parts with dots)");
        }

        // ✅ CRITICAL: Detect obvious corruption (control characters, invalid UTF-8 sequences)
        // Check for suspicious byte sequences like BOM or binary data
        for (int i = 0; i < token.length(); i++) {
            char c = token.charAt(i);
            // JWT should only contain Base64URL chars: A-Z, a-z, 0-9, -, _, .
            // Reject if we see control characters (< 32), high bytes (> 127), or other invalid chars
            if (c < 32 || (c > 127 && c != '.')) {
                logger.warn("Invalid token: contains suspicious characters (possible corruption/encoding issue)");
                throw new UnauthorizedException("Token contains invalid characters");
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