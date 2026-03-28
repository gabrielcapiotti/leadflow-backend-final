package com.leadflow.backend.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.leadflow.backend.entities.StripeCustomer;

import java.util.Optional;
import java.util.UUID;

/**
 * Repository for Stripe Customer mapping (critical for webhook processing)
 * 
 * DESIGN: Lookup by stripeCustomerId is the primary webhook path
 * All queries must be fast and stateless
 */
@Repository
public interface StripeCustomerRepository extends JpaRepository<StripeCustomer, UUID> {

    /**
     * PRIMARY LOOKUP: Find mapping by Stripe customer ID
     * 
     * Used by webhook handlers at message arrival time
     * MUST be fast and reliable
     * 
     * @param stripeCustomerId Stripe API customer ID (e.g., cus_xxx)
     * @return Optional containing mapping if found, empty otherwise
     */
    Optional<StripeCustomer> findByStripeCustomerId(String stripeCustomerId);

    /**
     * SECURITY: Find mapping by tenant + stripe customer ID
     * Prevents cross-tenant data leaks
     * 
     * @param tenantId Tenant UUID
     * @param stripeCustomerId Stripe customer ID
     * @return Optional containing mapping if found and belongs to tenant
     */
    @Query("""
        SELECT sc FROM StripeCustomer sc
        WHERE sc.tenant.id = :tenantId
        AND sc.stripeCustomerId = :stripeCustomerId
        """)
    Optional<StripeCustomer> findByTenantIdAndStripeCustomerId(
        @Param("tenantId") UUID tenantId,
        @Param("stripeCustomerId") String stripeCustomerId
    );

    /**
     * Verify mapping exists for tenant
     * Used for existence checks before webhook processing
     * 
     * @param tenantId Tenant UUID
     * @param stripeCustomerId Stripe customer ID
     * @return true if mapping exists and is active
     */
    @Query("""
        SELECT COUNT(sc) > 0 FROM StripeCustomer sc
        WHERE sc.tenant.id = :tenantId
        AND sc.stripeCustomerId = :stripeCustomerId
        AND sc.status = 'active'
        """)
    boolean existsActiveMapping(
        @Param("tenantId") UUID tenantId,
        @Param("stripeCustomerId") String stripeCustomerId
    );

    /**
     * Find mapping by subscription ID
     * Useful for subscription-specific operations
     * 
     * @param subscriptionId Local subscription ID (Long)
     * @return Optional containing mapping if found
     */
    Optional<StripeCustomer> findBySubscriptionId(Long subscriptionId);

    /**
     * Check if stripe customer is already mapped
     * Used during checkout to verify if customer is new or existing
     * 
     * @param stripeCustomerId Stripe customer ID
     * @return true if already mapped locally
     */
    boolean existsByStripeCustomerId(String stripeCustomerId);
}
