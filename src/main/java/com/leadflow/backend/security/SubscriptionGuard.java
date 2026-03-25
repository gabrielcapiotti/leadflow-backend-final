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
       ACCESS RESOLUTION (CORRIGIDO)
       ====================================================== */

    public SubscriptionAccessLevel resolveAccess() {

        // 🔥 HARD BYPASS: evita QUALQUER dependência de DB
        if (!billingEnabled) {
            log.debug("Billing disabled → FULL access granted");
            return SubscriptionAccessLevel.FULL;
        }

        try {
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

        } catch (AccessDeniedException ex) {

            // 🔥 NÃO quebrar fluxo — tratar como bloqueado
            log.warn("Access resolution failed: {}", ex.getMessage());
            return SubscriptionAccessLevel.BLOCKED;
        }
    }

    /* ======================================================
       SAFE ACCESS METHODS (USAR NO CONTROLLER)
       ====================================================== */

    public boolean isActive() {
        return resolveAccess() != SubscriptionAccessLevel.BLOCKED;
    }

    public boolean hasFullAccess() {
        return resolveAccess() == SubscriptionAccessLevel.FULL;
    }

    /* ======================================================
       STRICT ASSERTIONS (USAR COM CUIDADO)
       ====================================================== */

    public void assertActive() {
        if (!isActive()) {
            throw new AccessDeniedException("Subscription inactive");
        }
    }

    public void assertFullAccess() {
        if (!hasFullAccess()) {
            throw new AccessDeniedException("Write operation not allowed for current subscription");
        }
    }

    /* ======================================================
       INTERNAL RESOLUTION (ISOLADO)
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
            log.error("TenantContext is NULL for user={}", maskEmail(email));
            throw new AccessDeniedException("Tenant context not resolved");
        }

        return vendorRepository
                .findByUserEmailAndTenantId(email, tenant)
                .stream()
                .findFirst()
                .orElseThrow(() -> {
                    log.warn("Vendor not found → user={}, tenant={}",
                            maskEmail(email), tenant);
                    return new AccessDeniedException("Vendor not found");
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