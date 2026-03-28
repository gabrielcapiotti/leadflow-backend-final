package com.leadflow.backend.service.billing;

import com.google.gson.JsonObject;
import com.stripe.model.Event;
import com.leadflow.backend.util.StripeEventJsonExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles invoice.payment_failed webhook events from Stripe.
 * This event is triggered when an invoice payment fails.
 * 
 * Flow:
 * 1. Extract Invoice data from raw JSON (works with both real & mock payloads)
 * 2. Validate customer ID exists
 * 3. Call BillingService.handleInvoicePaymentFailed()
 *    → Updates subscription status to PAST_DUE
 * 
 * Why Raw JSON?
 * - SDK deserialization fails on mock payloads (missing api_version, livemode)
 * - Raw JSON works with both real Stripe webhooks AND test mocks
 * - Extraction is simple for required fields
 * 
 * Robustness:
 * - Safe JSON extraction with field validation
 * - Required field validation with fallback logging
 * - Raw payload logging on failure
 * 
 * Reference: https://stripe.com/docs/api/events/types#event_types-invoice.payment_failed
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InvoicePaymentFailedHandler implements StripeEventHandler {
    
    private final BillingService billingService;
    
    @Override
    public String getEventType() {
        return "invoice.payment_failed";
    }
    
    @Override
    public void handle(Event event) throws Exception {
        // Extract invoice data from raw JSON using dedicated extractor
        JsonObject invoiceJson = StripeEventJsonExtractor.extractDataObjectAsJson(event);
        if (invoiceJson == null) {
            log.warn("[HANDLER] Skipping invoice.payment_failed: Could not extract data object from event");
            return;
        }
        
        // Extract required fields from JSON
        String invoiceId = StripeEventJsonExtractor.getString(invoiceJson, "id");
        String stripeCustomerId = StripeEventJsonExtractor.getString(invoiceJson, "customer");
        Long nextPaymentAttempt = StripeEventJsonExtractor.getLong(invoiceJson, "next_payment_attempt");
        
        if (invoiceId == null || stripeCustomerId == null) {
            log.warn("[HANDLER] Skipping: Missing required fields - invoiceId={}, customerId={}", 
                invoiceId, stripeCustomerId);
            return;
        }
        
        log.info("[HANDLER] invoice.payment_failed: invoiceId={}, customerId={}, nextRetry={}", 
            invoiceId, stripeCustomerId, nextPaymentAttempt);
        
        // Delegate to BillingService for subscription update
        billingService.handleInvoicePaymentFailed(stripeCustomerId);
        
        log.info("[HANDLER] ✅ invoice.payment_failed processed: invoiceId={}, customerId={}", 
            invoiceId, stripeCustomerId);
    }
}
