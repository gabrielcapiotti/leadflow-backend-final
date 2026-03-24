package com.leadflow.backend.security;

import com.leadflow.backend.multitenancy.filter.TenantFilter;
import com.leadflow.backend.multitenancy.resolver.TenantResolver;
import com.leadflow.backend.security.jwt.JwtAuthenticationFilter;
import com.leadflow.backend.security.jwt.JwtService;
import com.leadflow.backend.multitenancy.service.TenantService;
import com.leadflow.backend.security.tenant.HibernateFilterService;
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
       TENANT FILTER
       ===================================================== */

    @Bean
    public TenantFilter tenantFilter(
            TenantResolver tenantResolver,
            HibernateFilterService hibernateFilterService
    ) {
        return new TenantFilter(tenantResolver, hibernateFilterService);
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
                .authenticationEntryPoint((request, response, authException) ->
                        response.sendError(HttpServletResponse.SC_UNAUTHORIZED)
                )
                .accessDeniedHandler((request, response, accessDeniedException) ->
                        response.sendError(HttpServletResponse.SC_FORBIDDEN)
                )
            )

            .authorizeHttpRequests(auth -> auth

                /* PUBLIC AUTH */
                .requestMatchers(
                        "/auth/register",
                        "/auth/login",
                        "/auth/refresh",
                        "/auth/forgot-password",
                        "/auth/reset-password",
                        "/auth/debug",
                        "/error"
                ).permitAll()

                /* PUBLIC API */
                .requestMatchers("/public/**").permitAll()

                /* AUTH REQUIRED */
                .requestMatchers(
                        "/auth/me",
                        "/auth/sessions/**",
                        "/auth/change-password",
                        "/auth/logout"
                ).authenticated()

                /* ADMIN */
                .requestMatchers("/admin/**").hasRole("ADMIN")

                /* WEBHOOKS / BILLING */
                .requestMatchers("/stripe/webhook").permitAll()
                .requestMatchers("/webhooks/**").permitAll()
                .requestMatchers("/billing/**").permitAll()

                /* MONITORING */
                .requestMatchers("/health").permitAll()
                .requestMatchers("/actuator/**").permitAll()

                /* BUSINESS */
                .requestMatchers("/vendors/**").authenticated()
                .requestMatchers("/api/vendor-leads/**").authenticated()

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
           FILTER ORDER (CORRETO)
           ========================================= */

        // ✅ 1. Tenant FIRST (define contexto)
        http.addFilterBefore(
                tenantFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        // ✅ 2. JWT AFTER (usa tenant já definido)
        if (jwtFilter != null) {
            http.addFilterAfter(
                    jwtFilter,
                    TenantFilter.class
            );
        }

        // Rate limiting
        http.addFilterAfter(
                rateLimitFilter,
                UsernamePasswordAuthenticationFilter.class
        );

        return http.build();
    }
}