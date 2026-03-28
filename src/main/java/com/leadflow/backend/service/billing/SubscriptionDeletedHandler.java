package com.leadflow.backend.service.billing;

import com.google.gson.JsonObject;
import com.leadflow.backend.entities.Subscription;
import com.leadflow.backend.repository.SubscriptionRepository;
import com.stripe.model.Event;
import com.leadflow.backend.util.StripeEventJsonExtractor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;

/**
 * Handles customer.subscription.deleted webhook events from Stripe.
 * This event is triggered when a subscription is deleted (either by the customer or after non-payment).
 * 
 * Flow:
 * 1. Extract Subscription data from raw JSON (works with both real & mock payloads)
 * 2. Look up internal subscription by stripeSubscriptionId
 * 3. Update status to CANCELLED and set cancelledAt timestamp
 * 4. Save updated subscription
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
public class SubscriptionDeletedHandler implements StripeEventHandler {
    
    private final BillingService billingService;
    private final SubscriptionRepository subscriptionRepository;
    
    @Override
    public String getEventType() {
        return "customer.subscription.deleted";
    }
    
    @Override
    @Transactional
    public void handle(Event event) throws Exception {
        // Extract subscription data from raw JSON using dedicated extractor
        JsonObject subscriptionJson = StripeEventJsonExtractor.extractDataObjectAsJson(event);
        if (subscriptionJson == null) {
            log.warn("[HANDLER] Skipping customer.subscription.deleted: Could not extract data object from event");
            return;
        }
        
        // Extract required fields from JSON
        String subscriptionId = StripeEventJsonExtractor.getString(subscriptionJson, "id");
        String stripeCustomerId = StripeEventJsonExtractor.getString(subscriptionJson, "customer");
        Long canceledAt = StripeEventJsonExtractor.getLong(subscriptionJson, "canceled_at");
        
        if (subscriptionId == null || stripeCustomerId == null) {
            log.warn("[HANDLER] Skipping: Missing required fields - subscriptionId={}, customerId={}", 
                subscriptionId, stripeCustomerId);
            return;
        }
        
        log.info("[HANDLER] customer.subscription.deleted: subscriptionId={}, customerId={}, canceledAt={}", 
            subscriptionId, stripeCustomerId, canceledAt);
        
        // Look up internal subscription by Stripe ID
        Subscription internalSubscription = subscriptionRepository
            .findByStripeSubscriptionId(subscriptionId)
            .orElse(null);
        
        if (internalSubscription == null) {
            log.warn("[HANDLER] Subscription not found in internal DB: stripeSubId={}", subscriptionId);
            // Still update via BillingService in case it's tracking the subscription differently
            billingService.handleSubscriptionCancelled(stripeCustomerId);
            return;
        }
        
        // Update subscription status to CANCELLED
        Subscription.SubscriptionStatus previousStatus = internalSubscription.getStatus();
        internalSubscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
        internalSubscription.setCancelledAt(LocalDateTime.now());
        
        subscriptionRepository.save(internalSubscription);
        
        log.info("[HANDLER] ✅ customer.subscription.deleted processed: subscriptionId={}, customerId={}, status: {} → CANCELLED", 
            subscriptionId, stripeCustomerId, previousStatus);
    }
}
