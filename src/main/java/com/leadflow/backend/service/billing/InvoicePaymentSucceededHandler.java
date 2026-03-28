package com.leadflow.backend.service.billing;

import com.google.gson.JsonObject;
import com.stripe.model.Event;
import com.leadflow.backend.util.StripeEventJsonExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles invoice.payment_succeeded webhook events from Stripe.
 * Also handles invoice.paid for compatibility.
 * 
 * Flow:
 * 1. Extract Invoice data from raw JSON (obras with both real & mock payloads)
 * 2. Validate customer ID exists
 * 3. Get periodEnd (epoch seconds) from invoice JSON
 * 4. Call BillingService.handleInvoicePaid()
 * 
 * Why Raw JSON?
 * - SDK deserialization fails on mock payloads
 * - Raw JSON works with both real Stripe webhooks AND test mocks
 * - Extraction is simple for required fields
 * 
 * Reference: https://stripe.com/docs/api/events/types#event_types-invoice.payment_succeeded
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class InvoicePaymentSucceededHandler implements StripeEventHandler {
    
    private final BillingService billingService;
    
    @Override
    public String getEventType() {
        return "invoice.payment_succeeded";
    }
    
    @Override
    public void handle(Event event) throws Exception {
        // Extract invoice data from raw JSON using dedicated extractor
        JsonObject invoiceJson = StripeEventJsonExtractor.extractDataObjectAsJson(event);
        if (invoiceJson == null) {
            log.warn("[HANDLER] Skipping invoice.payment_succeeded: Could not extract data object from event");
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
        
        log.info("[HANDLER] invoice.payment_succeeded: invoiceId={}, customerId={}, periodEnd={}", 
            invoiceId, stripeCustomerId, periodEndEpoch);
        
        // Delegate to BillingService for subscription update
        billingService.handleInvoicePaid(stripeCustomerId, periodEndEpoch);
        
        log.info("[HANDLER] ✅ invoice.payment_succeeded processed: invoiceId={}, customerId={}", 
            invoiceId, stripeCustomerId);
    }
}
