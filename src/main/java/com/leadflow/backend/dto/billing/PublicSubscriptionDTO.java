package com.leadflow.backend.dto.billing;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Subscription info accessible by authenticated users without vendor context
 * Used for test environment and public endpoints
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PublicSubscriptionDTO {
    
    @JsonProperty("subscription_status")
    private String subscriptionStatus;  // active, past_due, cancelled, pending
    
    @JsonProperty("customer_id")
    private String customerId;  // Stripe customer ID or null
    
    @JsonProperty("plan")
    private String plan;  // free, pro, enterprise
    
    @JsonProperty("current_period_start")
    private Long currentPeriodStart;  // Unix timestamp
    
    @JsonProperty("current_period_end")
    private Long currentPeriodEnd;  // Unix timestamp
    
    @JsonProperty("has_active_subscription")
    private boolean hasActiveSubscription;
    
    @JsonProperty("billing_email")
    private String billingEmail;
    
    @JsonProperty("is_test_environment")
    private boolean isTestEnvironment;  // Always true in test
    
    public static PublicSubscriptionDTO testDefault() {
        return PublicSubscriptionDTO.builder()
            .subscriptionStatus("active")
            .customerId(null)
            .plan("free")
            .currentPeriodStart(System.currentTimeMillis() / 1000)
            .currentPeriodEnd((System.currentTimeMillis() / 1000) + (30 * 24 * 60 * 60))
            .hasActiveSubscription(false)
            .billingEmail("test@leadflow.dev")
            .isTestEnvironment(true)
            .build();
    }
}
