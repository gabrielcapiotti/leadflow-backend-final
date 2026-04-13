package com.leadflow.backend.repository;

/**
 * STUB: Invoice repository for future integration.
 * Currently, Invoice data is not persisted locally.
 * 
 * When implemented, will provide:
 * - findByStripeInvoiceId(invoiceId)
 * - Invoice entity with: id, stripeInvoiceId, stripeSubscriptionId, tenantId, etc.
 * 
 * For now, invoice-based tenant resolution will:
 * 1. Attempt lookup (returns empty)
 * 2. Fall back to other resolution strategies
 */
public interface InvoiceRepository {
    // Stub: To be implemented when Invoice entity is created
}
