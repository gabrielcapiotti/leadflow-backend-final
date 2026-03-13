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

@Configuration
@EnableMethodSecurity
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
@ConditionalOnProperty(
        name = "security.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class SecurityWebConfig {

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
                    System.out.println("🔴 AuthenticationEntryPoint triggered for: " + request.getRequestURI());
                    System.out.println("🔴 Exception: " + authException.getMessage());
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
                        "/auth/register",
                        "/auth/login",
                        "/auth/refresh",
                        "/api/auth/register",
                        "/api/auth/login",
                        "/api/auth/refresh",
                        "/error"
                ).permitAll()

                /* =========================================
                   AUTHENTICATED AUTH ENDPOINTS
                   ========================================= */

                .requestMatchers(
                        "/auth/me",
                        "/auth/sessions/**",
                        "/api/auth/me",
                        "/api/auth/sessions/**"
                ).authenticated()

                /* =========================================
                   ADMIN
                   ========================================= */

                .requestMatchers("/admin/**").hasRole("ADMIN")

                /* =========================================
                   BILLING
                   ========================================= */

                .requestMatchers("/billing/checkout").permitAll()
                .requestMatchers("/billing/checkout-session").permitAll()
                .requestMatchers("/billing/webhook").permitAll()

                /* =========================================
                   WEBHOOKS
                   ========================================= */

                .requestMatchers("/stripe/webhook").permitAll()
                .requestMatchers("/payments/webhook").permitAll()
                .requestMatchers("/webhooks/**").permitAll()

                /* =========================================
                   MONITORING
                   ========================================= */

                .requestMatchers("/actuator/health").permitAll()
                .requestMatchers("/actuator/prometheus").permitAll()

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
           FILTER ORDER
           ========================================= */

        // Tenant must run BEFORE authentication
        http.addFilterBefore(
                tenantFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        if (jwtFilter != null) {
            http.addFilterBefore(
                    jwtFilter,
                    UsernamePasswordAuthenticationFilter.class
            );
        }

        http.addFilterAfter(
                rateLimitFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }
}

