package com.leadflow.backend.service.billing;

import com.google.gson.JsonObject;
import com.stripe.model.Event;
import com.leadflow.backend.util.StripeEventJsonExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles checkout.session.completed webhook events from Stripe.
 * This event is triggered when a checkout session is completed.
 * 
 * Flow:
 * 1. Extract checkout Session data from raw JSON (works with both real & mock payloads)
 * 2. Get stripeCustomerId and stripeSubscriptionId
 * 3. Call BillingService.handleCheckoutCompleted()
 *    → Links customer ID to subscription (lazy population)
 *    → Sets subscription status to ACTIVE
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
 * Use Case:
 * - Vendor completes payment via Stripe Checkout
 * - Order created with null stripeCustomerId
 * - Checkout succeeds → customer created in Stripe
 * - This handler links customer → order
 * 
 * Reference: https://stripe.com/docs/api/events/types#event_types-checkout.session.completed
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CheckoutSessionCompletedHandler implements StripeEventHandler {
    
    private final BillingService billingService;
    
    @Override
    public String getEventType() {
        return "checkout.session.completed";
    }
    
    @Override
    public void handle(Event event) throws Exception {
        // Extract session data from raw JSON using dedicated extractor
        JsonObject sessionJson = StripeEventJsonExtractor.extractDataObjectAsJson(event);
        if (sessionJson == null) {
            log.warn("[HANDLER] Skipping checkout.session.completed: Could not extract data object from event");
            return;
        }
        
        // Extract required fields from JSON
        String sessionId = StripeEventJsonExtractor.getString(sessionJson, "id");
        String stripeCustomerId = StripeEventJsonExtractor.getString(sessionJson, "customer");
        String stripeSubscriptionId = StripeEventJsonExtractor.getString(sessionJson, "subscription");
        
        if (sessionId == null || stripeCustomerId == null || stripeSubscriptionId == null) {
            log.warn("[HANDLER] Skipping: Missing required fields - sessionId={}, customerId={}, subscriptionId={}", 
                sessionId, stripeCustomerId, stripeSubscriptionId);
            return;
        }
        
        log.info("[HANDLER] checkout.session.completed: sessionId={}, customerId={}, subscriptionId={}", 
            sessionId, stripeCustomerId, stripeSubscriptionId);
        
        // Delegate to BillingService for checkout completion
        billingService.handleCheckoutCompleted(stripeCustomerId, stripeSubscriptionId);
        
        log.info("[HANDLER] ✅ checkout.session.completed processed: customerId={}, subscriptionId={}", 
            stripeCustomerId, stripeSubscriptionId);
    }
    
}
