package com.leadflow.backend.service.billing;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.leadflow.backend.entities.Subscription;
import com.leadflow.backend.repository.SubscriptionRepository;
import com.stripe.model.Event;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Handles customer.subscription.updated webhook events from Stripe.
 * This event is triggered when a subscription is updated (status change, plan change, etc).
 * 
 * Flow:
 * 1. Extract Subscription data from raw JSON (works with both real & mock payloads)
 * 2. Look up internal subscription by stripeSubscriptionId
 * 3. Sync status and period dates from Stripe data
 * 4. Save updated subscription
 * 
 * Why Raw JSON?
 * - SDK deserialization fails on mock payloads (missing api_version, livemode)
 * - Raw JSON works with both real Stripe webhooks AND test mocks
 * - Extraction is simple for required fields
 * 
 * Robustness:
 * - Safe JSON extraction with field validation
 * - Graceful degradation on missing fields
 * - Logs all subscription state changes
 * 
 * Reference: https://stripe.com/docs/api/events/types#event_types-customer.subscription.updated
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SubscriptionUpdatedHandler implements StripeEventHandler {
    
    private final SubscriptionRepository subscriptionRepository;
    
    @Override
    public String getEventType() {
        return "customer.subscription.updated";
    }
    
    @Override
    @Transactional
    public void handle(Event event) throws Exception {
        // Extract subscription data from raw JSON (works with mock payloads)
        String rawJson = event.getDataObjectDeserializer().getRawJson();
        if (rawJson == null || rawJson.isBlank()) {
            log.warn("[HANDLER] Skipping customer.subscription.updated: No raw JSON in event");
            return;
        }
        
        JsonObject dataObject = JsonParser.parseString(rawJson).getAsJsonObject();
        if (!dataObject.has("object") || dataObject.get("object").isJsonNull()) {
            log.warn("[HANDLER] Skipping customer.subscription.updated: No data object in event");
            return;
        }
        
        JsonObject subscriptionJson = dataObject.get("object").getAsJsonObject();
        
        // Extract subscription ID and fields from JSON
        String subscriptionId = extractStringField(subscriptionJson, "id");
        String stripeStatus = extractStringField(subscriptionJson, "status");
        Long currentPeriodStart = extractLongField(subscriptionJson, "current_period_start");
        Long currentPeriodEnd = extractLongField(subscriptionJson, "current_period_end");
        
        if (subscriptionId == null) {
            log.warn("[HANDLER] Skipping: Missing subscription ID");
            return;
        }
        
        log.info("[HANDLER] customer.subscription.updated: subscriptionId={}, status={}, periodEnd={}", 
            subscriptionId, stripeStatus, currentPeriodEnd);
        
        // Look up internal subscription by Stripe ID
        Subscription internalSubscription = subscriptionRepository
            .findByStripeSubscriptionId(subscriptionId)
            .orElse(null);
        
        if (internalSubscription == null) {
            log.warn("[HANDLER] Subscription not found in internal DB: stripeSubId={}", subscriptionId);
            return;
        }
        
        // Sync status from Stripe
        Subscription.SubscriptionStatus previousStatus = internalSubscription.getStatus();
        Subscription.SubscriptionStatus newStatus = mapStripeStatus(stripeStatus);
        internalSubscription.setStatus(newStatus);
        
        log.debug("[HANDLER] Status mapped: Stripe='{}' → Enum='{}' (was {})", 
                stripeStatus, newStatus, previousStatus);
        
        // Sync period dates from Stripe
        if (currentPeriodStart != null && currentPeriodStart > 0) {
            LocalDateTime startedAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(currentPeriodStart),
                ZoneId.systemDefault()
            );
            internalSubscription.setStartedAt(startedAt);
        }
        
        if (currentPeriodEnd != null && currentPeriodEnd > 0) {
            LocalDateTime expiresAt = LocalDateTime.ofInstant(
                Instant.ofEpochSecond(currentPeriodEnd),
                ZoneId.systemDefault()
            );
            internalSubscription.setExpiresAt(expiresAt);
        }
        
        // ⚠️ VALIDATION: garantir que status nunca é null antes de salvar
        if (internalSubscription.getStatus() == null) {
            log.error("❌ [VALIDATION] Status é NULL após mapeamento! stripeStatus={}", stripeStatus);
            internalSubscription.setStatus(Subscription.SubscriptionStatus.INCOMPLETE);
        }
        
        subscriptionRepository.save(internalSubscription);
        
        log.info("[HANDLER] ✅ Synced subscription with Stripe: subscriptionId={}, status: {} → {}", 
            subscriptionId, previousStatus, newStatus);
    }
    
    /**
     * Map Stripe status string to internal SubscriptionStatus enum.
     */
    private Subscription.SubscriptionStatus mapStripeStatus(String stripeStatus) {
        if (stripeStatus == null) {
            return Subscription.SubscriptionStatus.INCOMPLETE;
        }
        
        return switch (stripeStatus.toLowerCase()) {
            case "active" -> Subscription.SubscriptionStatus.ACTIVE;
            case "past_due" -> Subscription.SubscriptionStatus.PAST_DUE;
            case "canceled", "cancelled" -> Subscription.SubscriptionStatus.CANCELLED;
            case "incomplete" -> Subscription.SubscriptionStatus.INCOMPLETE;
            case "incomplete_expired" -> Subscription.SubscriptionStatus.INCOMPLETE;
            default -> Subscription.SubscriptionStatus.INCOMPLETE;
        };
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
     * Returns null if field missing or not a number.
     */
    private Long extractLongField(JsonObject json, String fieldName) {
        try {
            if (json.has(fieldName) && !json.get(fieldName).isJsonNull()) {
                return json.get(fieldName).getAsLong();
            }
        } catch (ClassCastException | IllegalStateException e) {
            log.debug("Failed to extract long field '{}': {}", fieldName, e.getMessage());
        }
        return null;
    }
}
