package com.leadflow.backend.security;

import com.leadflow.backend.multitenancy.filter.TenantFilter;
import com.leadflow.backend.security.jwt.JwtAuthenticationFilter;
import com.leadflow.backend.security.jwt.JwtService;
import com.leadflow.backend.multitenancy.service.TenantService;
import com.leadflow.backend.service.auth.UserSessionService;

import jakarta.servlet.http.HttpServletResponse;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.core.annotation.Order;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;

import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.security.web.header.writers.XXssProtectionHeaderWriter;
import org.springframework.web.cors.CorsConfigurationSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        name = "security.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SecurityWebConfig {

    private static final Logger log = LoggerFactory.getLogger(SecurityWebConfig.class);

    /* =====================================================
       AUTHENTICATION MANAGER
       ===================================================== */

    @Bean
    public AuthenticationManager authenticationManager(
            AuthenticationConfiguration configuration
    ) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /* =====================================================
       JWT FILTER
       ===================================================== */

    @Bean
    @ConditionalOnProperty(
            name = "security.jwt.enabled",
            havingValue = "true",
            matchIfMissing = true
    )
    public JwtAuthenticationFilter jwtAuthenticationFilter(
            JwtService jwtService,
            UserDetailsService userDetailsService,
            UserSessionService userSessionService,
            TenantService tenantService
    ) {
        return new JwtAuthenticationFilter(
                jwtService,
                userDetailsService,
                userSessionService,
                tenantService
        );
    }

    /* =====================================================
       SECURITY FILTER CHAIN
       ===================================================== */

    @Bean
    @Order(2)
    public SecurityFilterChain filterChain(
            HttpSecurity http,
            ObjectProvider<JwtAuthenticationFilter> jwtFilterProvider,
            RateLimitFilter rateLimitFilter,
            CorsConfigurationSource corsConfigurationSource,
            TenantFilter tenantFilter
    ) throws Exception {

        http
            .securityMatcher("/**")

            .csrf(csrf -> csrf.disable())

            .sessionManagement(session ->
                session.sessionCreationPolicy(SessionCreationPolicy.STATELESS)
            )

            .httpBasic(httpBasic -> httpBasic.disable())
            .formLogin(form -> form.disable())

            .cors(cors -> cors.configurationSource(corsConfigurationSource))

            .exceptionHandling(ex -> ex
                .authenticationEntryPoint((request, response, authException) -> {
                    log.error("🔴 AuthenticationEntryPoint triggered for: {}", request.getRequestURI());
                    log.error("🔴 Exception: {}", authException.getMessage());
                    response.sendError(HttpServletResponse.SC_UNAUTHORIZED);
                })
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        response.sendError(HttpServletResponse.SC_FORBIDDEN)
                )
            )

            .authorizeHttpRequests(auth -> auth

                /* =========================================
                   PUBLIC AUTH ENDPOINTS
                   ========================================= */

                .requestMatchers(
                        "/auth/**",
                        "/api/auth/**"
                ).permitAll()

                /* =========================================
                   AUTHENTICATED AUTH ENDPOINTS
                   ========================================= */

                .requestMatchers(
                        "/auth/me",
                        "/auth/sessions/**",
                        "/auth/change-password",
                        "/auth/logout",
                        "/auth/sessions",
                        "/api/auth/me",
                        "/api/auth/sessions/**",
                        "/api/auth/change-password",
                        "/api/auth/logout",
                        "/api/auth/sessions"
                ).authenticated()

                /* =========================================
                   ADMIN
                   ========================================= */

                .requestMatchers("/admin/**", "/api/admin/**").hasRole("ADMIN")

                /* =========================================
                   BILLING
                   ========================================= */

                .requestMatchers("/billing/checkout", "/api/billing/checkout").permitAll()
                .requestMatchers("/billing/checkout-session", "/api/billing/checkout-session").permitAll()
                .requestMatchers("/billing/webhook", "/api/billing/webhook").permitAll()
                .requestMatchers("/billing/subscription", "/api/billing/subscription").authenticated()
                .requestMatchers("/billing/usage", "/api/billing/usage").authenticated()
                .requestMatchers("/billing/test/get-tenant-id", "/api/billing/test/get-tenant-id").permitAll()
                .requestMatchers("/billing/test/create-stripe-mappings", "/api/billing/test/create-stripe-mappings").permitAll()

                /* =========================================
                   WEBHOOKS
                   ========================================= */

                .requestMatchers("/stripe/webhook", "/api/stripe/webhook").permitAll()
                .requestMatchers("/payments/webhook", "/api/payments/webhook").permitAll()
                .requestMatchers("/webhooks/**", "/api/webhooks/**").permitAll()

                /* =========================================
                   MONITORING
                   ========================================= */

                .requestMatchers("/actuator/health", "/api/actuator/health").permitAll()
                .requestMatchers("/actuator/prometheus", "/api/actuator/prometheus").permitAll()

                /* =========================================
                   PUBLIC API ENDPOINTS
                   ========================================= */

                .requestMatchers("/public/**", "/api/public/**").permitAll()

                /* =========================================
                   VENDORS
                   ========================================= */

                .requestMatchers("/vendors", "/api/vendors").authenticated()
                .requestMatchers("/vendor-leads/**", "/api/vendor-leads/**").authenticated()

                /* =========================================
                   NOTIFICATIONS
                   ========================================= */

                .requestMatchers("/api/v1/notifications/**").authenticated()

                /* =========================================
                   EVERYTHING ELSE
                   ========================================= */

                .anyRequest().authenticated()
            )

            .headers(headers -> headers
                .contentSecurityPolicy(csp ->
                    csp.policyDirectives("default-src 'self'")
                )
                .frameOptions(frame -> frame.deny())
                .xssProtection(xss ->
                    xss.headerValue(
                        XXssProtectionHeaderWriter.HeaderValue.ENABLED_MODE_BLOCK
                    )
                )
                .contentTypeOptions(contentType -> {})
                .httpStrictTransportSecurity(hsts -> hsts
                    .includeSubDomains(true)
                    .maxAgeInSeconds(31536000)
                )
                .referrerPolicy(referrer ->
                    referrer.policy(
                        ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER
                    )
                )
            );

        JwtAuthenticationFilter jwtFilter = jwtFilterProvider.getIfAvailable();

        /* =========================================
           FILTER ORDER - CRITICAL!
           ========================================= */

        // IMPORTANT: Filter execution order is crucial!
        // TenantFilter MUST run before JwtAuthenticationFilter
        // 
        // Execution order will be:
        // 1. TenantFilter (extracts X-Tenant-ID header and sets TenantContext) 
        // 2. JwtAuthenticationFilter (uses TenantContext that was already set by TenantFilter)
        // 3. UsernamePasswordAuthenticationFilter
        // 4. Rate limit filter (runs after authentication)

        // Add TenantFilter FIRST before UsernamePasswordAuthenticationFilter
        // This ensures TenantContext is available for JwtAuthenticationFilter
        if (tenantFilter != null) {
            http.addFilterBefore(
                    tenantFilter,
                    UsernamePasswordAuthenticationFilter.class
            );
        }

        // Add JwtAuthenticationFilter AFTER TenantFilter (not before)
        // This ensures deterministic order: TenantFilter → JwtAuthenticationFilter
        if (jwtFilter != null) {
            http.addFilterAfter(
                    jwtFilter,
                    TenantFilter.class
            );
        }

        // Rate limit filter runs after authentication
        http.addFilterAfter(
                rateLimitFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }
}

