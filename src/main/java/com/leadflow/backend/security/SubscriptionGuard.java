package com.leadflow.backend.security;

import com.leadflow.backend.entities.vendor.SubscriptionAccessLevel;
import com.leadflow.backend.entities.vendor.SubscriptionStatus;
import com.leadflow.backend.entities.vendor.Vendor;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.VendorRepository;
import com.leadflow.backend.service.vendor.SubscriptionService;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Objects;

@Component
public class SubscriptionGuard {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionGuard.class);

    private final VendorRepository vendorRepository;
    private final SubscriptionService subscriptionService;

    @Value("${app.billing.enabled:false}")
    private boolean billingEnabled;

    public SubscriptionGuard(VendorRepository vendorRepository,
                             SubscriptionService subscriptionService) {

        this.vendorRepository = Objects.requireNonNull(vendorRepository);
        this.subscriptionService = Objects.requireNonNull(subscriptionService);
    }

    /* ======================================================
       ACCESS RESOLUTION
       ====================================================== */

    public SubscriptionAccessLevel resolveAccess() {

        if (!billingEnabled) {
            log.debug("Billing disabled → FULL access granted");
            return SubscriptionAccessLevel.FULL;
        }

        Vendor vendor = resolveVendorStrict();

        SubscriptionAccessLevel level =
                subscriptionService.getAccessLevel(vendor);

        if (isExpired(vendor) &&
            level != SubscriptionAccessLevel.BLOCKED) {

            log.warn("Subscription expired → forcing BLOCKED (vendorId={})",
                    vendor.getId());

            return SubscriptionAccessLevel.BLOCKED;
        }

        return level;
    }

    /* ======================================================
       ASSERTIONS
       ====================================================== */

    public boolean isActive() {
        try {
            return resolveAccess() != SubscriptionAccessLevel.BLOCKED;
        } catch (AccessDeniedException ex) {
            return false;
        }
    }

    public void assertActive() {
        if (resolveAccess() == SubscriptionAccessLevel.BLOCKED) {
            throw new AccessDeniedException("Subscription inactive");
        }
    }

    public void assertFullAccess() {
        if (resolveAccess() != SubscriptionAccessLevel.FULL) {
            throw new AccessDeniedException("Write operation not allowed for current subscription");
        }
    }

    /* ======================================================
       INTERNAL RESOLUTION (STRICT)
       ====================================================== */

    private Vendor resolveVendorStrict() {

        Authentication authentication =
                SecurityContextHolder.getContext().getAuthentication();

        if (authentication == null ||
            !authentication.isAuthenticated() ||
            authentication instanceof AnonymousAuthenticationToken) {

            throw new AccessDeniedException("Authentication required");
        }

        String email = authentication.getName();

        if (email == null || email.isBlank()) {
            throw new AccessDeniedException("Invalid authentication principal");
        }

        String tenant = TenantContext.getTenant();

        if (tenant == null || tenant.isBlank()) {
            log.error("TenantContext is NULL or empty for user={}", maskEmail(email));
            throw new AccessDeniedException("Tenant context not resolved");
        }

        log.debug("Resolving vendor → user={}, tenant={}",
                maskEmail(email), tenant);

        return vendorRepository
                .findByUserEmailAndTenantId(email, tenant)
                .stream()
                .findFirst()
                .orElseThrow(() -> {
                    log.error("Vendor not found → user={}, tenant={}",
                            maskEmail(email), tenant);
                    return new AccessDeniedException("Vendor not found for current tenant");
                });
    }

    /* ======================================================
       HELPERS
       ====================================================== */

    private String maskEmail(String email) {
        if (email == null || email.length() < 5) {
            return "***";
        }
        return email.substring(0, 2) + "***@***";
    }

    private boolean isExpired(Vendor vendor) {
        return vendor.getSubscriptionExpiresAt() != null &&
               vendor.getSubscriptionExpiresAt().isBefore(Instant.now()) &&
               vendor.getSubscriptionStatus() != SubscriptionStatus.INADIMPLENTE;
    }
}