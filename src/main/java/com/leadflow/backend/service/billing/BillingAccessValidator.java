package com.leadflow.backend.service.billing;

import com.leadflow.backend.entities.Subscription;
import com.leadflow.backend.exception.BillingException;
import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.repository.SubscriptionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * BillingAccessValidator — CRITICAL SECURITY GATE
 *
 * Enforces that:
 * 1. Subscription exists for tenant
 * 2. Subscription status is ACTIVE (paid)
 * 3. Prevents unpaid users from accessing system
 *
 * MUST be called before any paid feature access:
 * - AI endpoints
 * - Lead creation
 * - Advanced features
 *
 * Architecture:
 * - Billing is NOT optional (app.billing.enabled = true)
 * - Users loggedIn WITHOUT active subscription should be BLOCKED
 * - Only trial or paid ACTIVE subscriptions are allowed
 *
 * Design Pattern:
 * - Guard pattern (fail-fast)
 * - Throws BillingException on validation failure
 * - Logs all access denials for audit
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class BillingAccessValidator {

    private final SubscriptionRepository subscriptionRepository;

    /**
     * Validate that tenant has ACTIVE subscription
     *
     * MUST be called for:
     * - All paid AI features
     * - Lead creation / management
     * - Advanced operations
     *
     * Allowed states:
     * - ACTIVE (paid)
     * - TRIALING (trial period w/ payment method on file)
     *
     * NOT allowed:
     * - PENDING_PAYMENT (checkout incomplete)
     * - CANCELED
     * - EXPIRED
     * - null (subscription never created)
     *
     * @param tenantId UUID of tenant making request
     * @throws BillingException if subscription invalid or missing
     */
    public void validateActiveSubscription(UUID tenantId) {
        if (tenantId == null) {
            log.error("❌ BillingAccessValidator: tenantId is null");
            throw new BillingException("Tenant identification failed - cannot validate billing");
        }

        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElse(null);

        // CRITICAL: No subscription found = user hasn't completed onboarding
        if (subscription == null) {
            log.warn("🚫 BillingAccessValidator: Access DENIED - Subscription not found for tenant={}", tenantId);
            throw new BillingException(
                    "Subscription not found - please complete payment setup via checkout"
            );
        }

        // CRITICAL: Subscription must be ACTIVE or TRIALING
        // TRIALING = has payment method on file + trial period
        // PENDING_PAYMENT = checkout incomplete = NOT ALLOWED
        Subscription.SubscriptionStatus status = subscription.getStatus();

        if (status == null) {
            log.warn("🚫 BillingAccessValidator: Access DENIED - Subscription status is null for tenant={}", tenantId);
            throw new BillingException("Subscription status invalid - please contact support");
        }

        if (status != Subscription.SubscriptionStatus.ACTIVE && status != Subscription.SubscriptionStatus.TRIALING) {
            log.warn("🚫 BillingAccessValidator: Access DENIED - Subscription status={} for tenant={}", status, tenantId);
            throw new BillingException(
                    "Subscription is not active (status: " + status + ") - please complete payment"
            );
        }

        // ✅ Validation passed
        log.debug("✅ BillingAccessValidator: Access ALLOWED - Subscription status={}, tenant={}", status, tenantId);
    }

    /**
     * Soft check — returns boolean instead of throwing
     * Useful for conditional logic (e.g., show/hide paid features)
     *
     * @param tenantId UUID of tenant
     * @return true if subscription ACTIVE, false otherwise
     */
    public boolean hasActiveSubscription(UUID tenantId) {
        if (tenantId == null) {
            return false;
        }

        Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
                .orElse(null);

        if (subscription == null) {
            return false;
        }

        Subscription.SubscriptionStatus status = subscription.getStatus();
        return status == Subscription.SubscriptionStatus.ACTIVE || status == Subscription.SubscriptionStatus.TRIALING;
    }

    /**
     * Check using tenant from context (JWT)
     * Convenience method combining TenantContext + validation
     *
     * @throws BillingException if validation fails
     */
    public void validateFromContext() {
        UUID tenantId = TenantContext.requireTenant();
        validateActiveSubscription(tenantId);
    }
}
