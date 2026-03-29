package com.leadflow.backend.security;

import com.leadflow.backend.entities.Subscription;
import com.leadflow.backend.entities.vendor.SubscriptionAccessLevel;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.SubscriptionRepository;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;
import java.util.Optional;

@Component
public class SubscriptionGuard {

    private static final Logger log = LoggerFactory.getLogger(SubscriptionGuard.class);

    private final SubscriptionRepository subscriptionRepository;

    @Value("${app.billing.enabled:false}")
    private boolean billingEnabled;

    public SubscriptionGuard(SubscriptionRepository subscriptionRepository) {

        this.subscriptionRepository = Objects.requireNonNull(subscriptionRepository);
    }

    /* ======================================================
       ACCESS RESOLUTION (CORRIGIDO - NOW USES SUBSCRIPTION)
       ====================================================== */

    public SubscriptionAccessLevel resolveAccess() {

        // 🔥 HARD BYPASS: evita QUALQUER dependência de DB
        if (!billingEnabled) {
            log.debug("Billing disabled → FULL access granted");
            return SubscriptionAccessLevel.FULL;
        }

        try {
            // NEW: Get tenantId directly from TenantContext (simpler than email + tenantId query)
            String tenantStr = TenantContext.getTenant();
            if (tenantStr == null || tenantStr.isBlank()) {
                log.error("TenantContext is NULL");
                throw new AccessDeniedException("Tenant context not resolved");
            }

            UUID tenantId = UUID.fromString(tenantStr);

            // NEW: Query Subscription by tenantId (NOT via deprecated Vendor)
            Optional<Subscription> subscriptionOpt = subscriptionRepository.findByTenantId(tenantId);
            
            if (subscriptionOpt.isEmpty()) {
                log.warn("Subscription not found for tenant={}", tenantId);
                throw new AccessDeniedException("Subscription not found");
            }

            Subscription subscription = subscriptionOpt.get();

            // NEW: Map subscription status directly to access level
            SubscriptionAccessLevel level = mapSubscriptionToAccessLevel(subscription);

            // Check expiration
            if (isSubscriptionExpired(subscription) && 
                level != SubscriptionAccessLevel.BLOCKED) {
                log.warn("Subscription expired for tenant={}", tenantId);
                return SubscriptionAccessLevel.BLOCKED;
            }

            return level;

        } catch (AccessDeniedException ex) {
            log.warn("Access resolution failed: {}", ex.getMessage());
            throw ex;
        } catch (Exception ex) {
            log.error("❌ FAILED to resolve subscription access", ex);
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
       HELPERS
       ====================================================== */

    /* NEW: Map Subscription status to access level */
    private SubscriptionAccessLevel mapSubscriptionToAccessLevel(Subscription subscription) {
        if (subscription == null || subscription.getStatus() == null) {
            return SubscriptionAccessLevel.BLOCKED;
        }

        return switch (subscription.getStatus()) {
            case ACTIVE, TRIALING -> SubscriptionAccessLevel.FULL;
            case PAST_DUE -> SubscriptionAccessLevel.READ_ONLY;
            case CANCELLED, INCOMPLETE, COMPLETED -> SubscriptionAccessLevel.BLOCKED;
        };
    }

    /* NEW: Check if subscription has expired */
    private boolean isSubscriptionExpired(Subscription subscription) {
        if (subscription == null || subscription.getExpiresAt() == null) {
            return false;
        }
        
        LocalDateTime now = LocalDateTime.now();
        return subscription.getExpiresAt().isBefore(now);
    }
}