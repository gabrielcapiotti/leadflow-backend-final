package com.leadflow.backend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

/**
 * Centralized billing configuration service.
 *
 * Consolidates billing-related configuration from multiple locations
 * into a single, testable source of truth.
 *
 * REPLACES:
 * - BillingService.billingEnabled
 * - SubscriptionGuard.billingEnabled
 * - BillingValidationInterceptor.billingEnabled
 * - SubscriptionService.billingEnabled
 */
@Service
@Slf4j
public class BillingConfigService {

    @Value("${app.billing.enabled:false}")
    private boolean enabled;

    /**
     * Check if billing is enabled.
     *
     * @return true if billing feature is active, false otherwise
     */
    public boolean isEnabled() {
        return enabled;
    }

    /**
     * Require billing to be enabled.
     *
     * @throws IllegalStateException if billing is not enabled
     */
    public void requireEnabled() {
        if (!enabled) {
            throw new IllegalStateException("Billing is not enabled. Set app.billing.enabled=true");
        }
    }

    /**
     * Check if billing is disabled (inverse of isEnabled).
     *
     * @return true if billing feature is inactive, false otherwise
     */
    public boolean isDisabled() {
        return !enabled;
    }

    /**
     * Log initialization status.
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        log.info("💳 BillingConfigService initialized - billing.enabled={}", enabled);
    }
}
