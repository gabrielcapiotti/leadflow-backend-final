package com.leadflow.backend.service.billing;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.leadflow.backend.entities.StripeCustomer;
import com.leadflow.backend.entities.Subscription;
import com.leadflow.backend.entities.Tenant;
import com.leadflow.backend.repository.StripeCustomerRepository;
import com.leadflow.backend.repository.SubscriptionRepository;
import com.leadflow.backend.repository.tenant.TenantRepository;

import java.util.Optional;
import java.util.UUID;

/**
 * SOURCE OF TRUTH: Resolves Stripe customer_id → tenant_id mapping
 * 
 * CRITICAL FOR WEBHOOK PROCESSING
 * 
 * This service enables stateless, reliable webhook handling without
 * calling Stripe APIs at webhook time.
 * 
 * All webhook handlers MUST use this service to:
 * 1. Look up customer mapping
 * 2. Resolve tenant_id
 * 3. Set TenantContext
 * 4. Process subscription mutation
 * 
 * @author Webhook Mapping Service
 */
@Service
@Slf4j
@RequiredArgsConstructor
@Transactional
public class StripeCustomerMappingService {

    private final StripeCustomerRepository stripeCustomerRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final TenantRepository tenantRepository;

    /**
     * PRIMARY LOOKUP: Resolve tenant for Stripe customer
     * 
     * This is the GATEWAY for all webhook processing.
     * 
     * @param stripeCustomerId Stripe customer ID from event
     * @return Optional of tenant UUID if mapping found
     * @throws IllegalStateException if customer exists but mapping is broken
     */
    public Optional<UUID> resolveTenantIdForCustomer(String stripeCustomerId) {
        if (stripeCustomerId == null || stripeCustomerId.isBlank()) {
            log.warn("Stripe customer ID is null or blank in webhook");
            return Optional.empty();
        }

        Optional<StripeCustomer> mapping = stripeCustomerRepository.findByStripeCustomerId(stripeCustomerId);

        if (mapping.isEmpty()) {
            log.warn("No tenant mapping found for Stripe customer: {}", stripeCustomerId);
            return Optional.empty();
        }

        StripeCustomer customer = mapping.get();

        // Validate mapping integrity
        if (customer.getTenant() == null) {
            log.error("INCONSISTENCY: Stripe customer {} has null tenant!", stripeCustomerId);
            throw new IllegalStateException("Broken mapping: customer has no tenant");
        }

        if (customer.getTenant().getId() == null) {
            log.error("INCONSISTENCY: Stripe customer {} tenant has null ID!", stripeCustomerId);
            throw new IllegalStateException("Broken mapping: tenant has no ID");
        }

        UUID tenantId = customer.getTenant().getId();
        
        // Record webhook processed timestamp
        customer.recordWebhookProcessed();
        stripeCustomerRepository.save(customer);

        log.debug("Resolved tenant {} for Stripe customer {}", tenantId, stripeCustomerId);
        return Optional.of(tenantId);
    }

    /**
     * Create or update customer mapping
     * 
     * Called when:
     * - Checkout completes (first Stripe customer creation)
     * - Customer metadata updated
     * - Customer deleted (mark as inactive)
     * 
     * @param tenantId Local tenant UUID
     * @param stripeCustomerId Stripe customer ID
     * @param subscriptionId Optional local subscription ID (Long)
     * @return Created or updated StripeCustomer
     */
    public StripeCustomer createOrUpdateMapping(
        UUID tenantId,
        String stripeCustomerId,
        Long subscriptionId) {

        // Validate inputs
        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId cannot be null");
        }
        if (stripeCustomerId == null || stripeCustomerId.isBlank()) {
            throw new IllegalArgumentException("stripeCustomerId cannot be null or blank");
        }

        // Verify tenant exists
        Tenant tenant = tenantRepository.findById(tenantId)
            .orElseThrow(() -> new IllegalArgumentException("Tenant not found: " + tenantId));

        // Check for existing mapping
        Optional<StripeCustomer> existing = stripeCustomerRepository.findByStripeCustomerId(stripeCustomerId);

        StripeCustomer customer;
        if (existing.isPresent()) {
            customer = existing.get();
            log.debug("Updating existing Stripe customer mapping: {}", stripeCustomerId);
        } else {
            customer = StripeCustomer.builder()
                .tenant(tenant)
                .stripeCustomerId(stripeCustomerId)
                .status("active")
                .build();
            log.info("Creating new Stripe customer mapping: {} → tenant {}", stripeCustomerId, tenantId);
        }

        // Update subscription reference if provided
        if (subscriptionId != null) {
            Subscription subscription = subscriptionRepository.findById(subscriptionId)
                .orElseThrow(() -> new IllegalArgumentException("Subscription not found: " + subscriptionId));
            customer.setSubscription(subscription);
        }

        customer.setUpdatedAt(java.time.Instant.now());
        return stripeCustomerRepository.save(customer);
    }

    /**
     * Mark customer mapping as inactive (soft delete)
     * 
     * Called when customer.deleted event from Stripe
     * 
     * @param stripeCustomerId Stripe customer ID
     */
    public void markCustomerAsDeleted(String stripeCustomerId) {
        Optional<StripeCustomer> mapping = stripeCustomerRepository.findByStripeCustomerId(stripeCustomerId);

        if (mapping.isEmpty()) {
            log.warn("Attempted to mark non-existent customer as deleted: {}", stripeCustomerId);
            return;
        }

        StripeCustomer customer = mapping.get();
        customer.setStatus("deleted");
        customer.recordWebhookProcessed();
        stripeCustomerRepository.save(customer);

        log.info("Marked Stripe customer as deleted: {}", stripeCustomerId);
    }

    /**
     * Verify mapping is valid and active
     * 
     * Used by handlers to validate before processing
     * 
     * @param stripeCustomerId Stripe customer ID
     * @return true if mapping exists and is active
     */
    public boolean isValidActiveMapping(String stripeCustomerId) {
        Optional<StripeCustomer> mapping = stripeCustomerRepository.findByStripeCustomerId(stripeCustomerId);
        
        boolean result = mapping.isPresent() && "active".equals(mapping.get().getStatus());
        
        if (!result && mapping.isPresent()) {
            log.warn("Stripe customer {} exists but status is: {}", 
                stripeCustomerId, mapping.get().getStatus());
        }
        
        return result;
    }

    /**
     * Count mappings for tenant
     * Used for metrics/reporting
     * 
     * @param tenantId Tenant UUID
     * @return Number of active customers for tenant
     */
    public long countActiveCustomersForTenant(UUID tenantId) {
        // This would need a custom query in repository
        // Placeholder for now
        return 0L;
    }
}
