package com.leadflow.backend.repository;

/**
 * STUB: PaymentIntent repository for future integration.
 * Currently, PaymentIntent data is not persisted locally.
 * 
 * When implemented, will provide:
 * - findByStripePaymentIntentId(paymentIntentId)
 * - PaymentIntent entity with: id, stripePaymentIntentId, stripeCustomerId, tenantId, etc.
 * 
 * For now, paymentIntent-based tenant resolution will:
 * 1. Attempt lookup (returns empty)
 * 2. Fall back to other resolution strategies
 */
public interface PaymentIntentRepository {
    // Stub: To be implemented when PaymentIntent entity is created
}
