package com.leadflow.backend.service.billing;

import com.google.gson.JsonObject;
import com.stripe.model.Event;
import com.leadflow.backend.util.StripeEventJsonExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles invoice.paid webhook events from Stripe.
 * This is the actual event fired when an invoice payment completes.
 * 
 * Note: Stripe may fire BOTH invoice.payment_succeeded and invoice.paid.
 * This handler ensures both are processed identically.
 * 
 * Flow:
 * 1. Extract Invoice data from raw JSON (works with both real & mock payloads)
 * 2. Validate customer ID exists
 * 3. Get periodEnd (epoch seconds) from invoice JSON
 * 4. Call BillingService.handleInvoicePaid()
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
 * Idempotency: Handled by StripeEventIdempotencyService
 * (Both events with same invoice → idempotency ensures single update)
 * 
 * Reference: https://stripe.com/docs/api/events/types#event_types-invoice.paid
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InvoicePaidHandler implements StripeEventHandler {
    
    private final BillingService billingService;
    
    @Override
    public String getEventType() {
        return "invoice.paid";
    }
    
    @Override
    public void handle(Event event) throws Exception {
        // Extract invoice data from raw JSON using dedicated extractor
        JsonObject invoiceJson = StripeEventJsonExtractor.extractDataObjectAsJson(event);
        if (invoiceJson == null) {
            log.warn("[HANDLER] Skipping invoice.paid: Could not extract data object from event");
            return;
        }
        
        // Extract required fields from JSON
        String invoiceId = StripeEventJsonExtractor.getString(invoiceJson, "id");
        String stripeCustomerId = StripeEventJsonExtractor.getString(invoiceJson, "customer");
        Long periodEndEpoch = StripeEventJsonExtractor.getLong(invoiceJson, "period_end");
        
        if (invoiceId == null || stripeCustomerId == null) {
            log.warn("[HANDLER] Skipping: Missing required fields - invoiceId={}, customerId={}", 
                invoiceId, stripeCustomerId);
            return;
        }
        
        if (periodEndEpoch == null || periodEndEpoch == 0) {
            log.warn("[HANDLER] period_end missing for invoice: {}. Using current timestamp.", invoiceId);
            periodEndEpoch = System.currentTimeMillis() / 1000;
        }
        
        log.info("[HANDLER] invoice.paid: invoiceId={}, customerId={}, periodEnd={}", 
            invoiceId, stripeCustomerId, periodEndEpoch);
        
        // Delegate to BillingService for subscription update
        billingService.handleInvoicePaid(stripeCustomerId, periodEndEpoch);
        
        log.info("[HANDLER] ✅ invoice.paid processed: invoiceId={}, customerId={}", 
            invoiceId, stripeCustomerId);
    }
}
