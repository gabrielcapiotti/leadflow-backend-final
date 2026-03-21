package com.leadflow.backend.dto.billing;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.leadflow.backend.entities.Plan;
import com.leadflow.backend.entities.Subscription;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SubscriptionDetailsDTO {

    private Long subscriptionId;
    private String tenantId;
    private String stripeCustomerId;
    private String stripeSubscriptionId;
    private String status;
    private String planName;
    private Integer maxLeads;
    private Integer maxUsers;
    private Integer maxAiExecutions;

    @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd'T'HH:mm:ss'Z'")
    private LocalDateTime startedAt;

    private LocalDateTime expiresAt;
    private Long daysUntilExpiration;

    private Boolean isActive;
    private Boolean isPastDue;
    private Boolean isCancelled;

    private LocalDateTime createdAt;

    // =====================================================
    // SAFE MAPPER
    // =====================================================

    public static SubscriptionDetailsDTO fromEntity(Subscription subscription) {

        if (subscription == null) {
            return createEmptySubscription();
        }

        boolean isActive = subscription.getStatus() == Subscription.SubscriptionStatus.ACTIVE;
        boolean isPastDue = subscription.getStatus() == Subscription.SubscriptionStatus.PAST_DUE;
        boolean isCancelled = subscription.getStatus() == Subscription.SubscriptionStatus.CANCELLED;

        LocalDateTime now = LocalDateTime.now();

        Long daysUntil = null;
        if (subscription.getExpiresAt() != null) {
            daysUntil = java.time.temporal.ChronoUnit.DAYS
                    .between(now, subscription.getExpiresAt());
        }

        Plan plan = subscription.getPlan();

        return SubscriptionDetailsDTO.builder()
                .subscriptionId(subscription.getId())
                .tenantId(subscription.getTenantId() != null
                        ? subscription.getTenantId().toString()
                        : null)

                .stripeCustomerId(subscription.getStripeCustomerId())
                .stripeSubscriptionId(subscription.getStripeSubscriptionId())

                .status(subscription.getStatus() != null
                        ? subscription.getStatus().name()
                        : "UNKNOWN")

                // SAFE PLAN ACCESS
                .planName(plan != null ? plan.getName() : "FREE")
                .maxLeads(plan != null ? plan.getMaxLeads() : 0)
                .maxUsers(plan != null ? plan.getMaxUsers() : 0)
                .maxAiExecutions(plan != null ? plan.getMaxAiExecutions() : 0)

                .startedAt(subscription.getStartedAt())
                .expiresAt(subscription.getExpiresAt())
                .daysUntilExpiration(daysUntil)

                .isActive(isActive)
                .isPastDue(isPastDue)
                .isCancelled(isCancelled)

                .createdAt(subscription.getCreatedAt())
                .build();
    }

    // =====================================================
    // EMPTY FALLBACK
    // =====================================================

    public static SubscriptionDetailsDTO createEmptySubscription() {
        return SubscriptionDetailsDTO.builder()
                .subscriptionId(null)
                .tenantId(null)
                .stripeCustomerId(null)
                .stripeSubscriptionId(null)
                .status("NONE")
                .planName("FREE")
                .maxLeads(0)
                .maxUsers(0)
                .maxAiExecutions(0)
                .startedAt(null)
                .expiresAt(null)
                .daysUntilExpiration(0L)
                .isActive(false)
                .isPastDue(false)
                .isCancelled(false)
                .createdAt(null)
                .build();
    }
}