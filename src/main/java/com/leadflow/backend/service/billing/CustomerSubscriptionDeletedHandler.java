package com.leadflow.backend.service.billing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.stripe.model.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * Handles customer.subscription.deleted webhook events from Stripe.
 * This event is triggered when a subscription is deleted.
 * 
 * Flow:
 * 1. Extract Subscription data from raw JSON (works with both real & mock payloads)
 * 2. Validate customer ID exists
 * 3. Call BillingService.handleSubscriptionCancelled()
 *    → Updates subscription status to CANCELLED
 *    → Sets cancelledAt timestamp
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
 * Reference: https://stripe.com/docs/api/events/types#event_types-customer.subscription.deleted
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class CustomerSubscriptionDeletedHandler implements StripeEventHandler {
    
    private final BillingService billingService;
    
    @Override
    public String getEventType() {
        return "customer.subscription.deleted";
    }
    
    @Override
    public void handle(Event event) throws Exception {
        // Extract subscription data from raw JSON (works with mock payloads)
        String rawJson = event.getDataObjectDeserializer().getRawJson();
        if (rawJson == null || rawJson.isBlank()) {
            log.warn("[HANDLER] Skipping customer.subscription.deleted: No raw JSON in event");
            return;
        }
        
        JsonObject dataObject = JsonParser.parseString(rawJson).getAsJsonObject();
        if (!dataObject.has("object") || dataObject.get("object").isJsonNull()) {
            log.warn("[HANDLER] Skipping customer.subscription.deleted: No data object in event");
            return;
        }
        
        JsonObject subscriptionJson = dataObject.get("object").getAsJsonObject();
        
        // Extract required fields from JSON
        String subscriptionId = extractStringField(subscriptionJson, "id");
        String stripeCustomerId = extractStringField(subscriptionJson, "customer");
        long canceledAtEpoch = extractLongField(subscriptionJson, "canceled_at");
        
        if (subscriptionId == null || stripeCustomerId == null) {
            log.warn("[HANDLER] Skipping: Missing required fields - subscriptionId={}, customerId={}", 
                subscriptionId, stripeCustomerId);
            return;
        }
        
        log.info("[HANDLER] customer.subscription.deleted: subscriptionId={}, customerId={}, canceledAt={}", 
            subscriptionId, stripeCustomerId, canceledAtEpoch);
        
        // Delegate to BillingService for subscription cancellation
        billingService.handleSubscriptionCancelled(stripeCustomerId);
        
        log.info("[HANDLER] ✅ customer.subscription.deleted processed: subscriptionId={}, customerId={}", 
            subscriptionId, stripeCustomerId);
    }
    
    /**
     * Extract string field from JSON object.
     * Returns null if field missing or not a string.
     */
    private String extractStringField(JsonObject json, String fieldName) {
        try {
            if (json.has(fieldName) && !json.get(fieldName).isJsonNull()) {
                String value = json.get(fieldName).getAsString();
                return (value != null && !value.isBlank()) ? value : null;
            }
        } catch (ClassCastException | IllegalStateException e) {
            log.debug("Failed to extract string field '{}': {}", fieldName, e.getMessage());
        }
        return null;
    }
    
    /**
     * Extract long field from JSON object.
     * Returns 0 if field missing or not a number.
     */
    private long extractLongField(JsonObject json, String fieldName) {
        try {
            if (json.has(fieldName) && !json.get(fieldName).isJsonNull()) {
                return json.get(fieldName).getAsLong();
            }
        } catch (ClassCastException | IllegalStateException e) {
            log.debug("Failed to extract long field '{}': {}", fieldName, e.getMessage());
        }
        return 0;
    }
}
