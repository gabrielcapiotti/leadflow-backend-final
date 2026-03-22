package com.leadflow.backend.dto.billing;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Webhook Dashboard DTO - Provides comprehensive webhook metrics and status information.
 * Used for admin dashboard to monitor Stripe webhook processing health.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookDashboardDTO {

    private WebhookMetricsDTO metrics;
    private WebhookHealthDTO health;
    private List<RecentWebhookDTO> recentEvents;
    private FailureAnalysisDTO failureAnalysis;
    private CircuitBreakerStatusDTO circuitBreakerStatus;

    /**
     * Overall webhook processing metrics (aggregated across all tenants)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WebhookMetricsDTO {
        private Long totalWebhooksReceived;      // Total count of webhook events received
        private Long totalSuccessful;            // Successfully processed webhooks
        private Long totalFailed;                // Permanently failed webhooks
        private Long totalPendingRetry;          // Currently pending retry
        private Double successRate;              // % success rate (0.0-100.0)
        private Double averageProcessingTimeMs;  // Average time to process webhook
    }

    /**
     * Current health status of webhook processing system
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class WebhookHealthDTO {
        private String status;                   // OK, WARNING, CRITICAL, OPEN (circuit breaker)
        private String message;                  // Human-readable status message
        private Integer pendingRetryCount;       // Number of events awaiting retry
        private LocalDateTime lastProcessedAt;   // Last successful webhook processed
        private LocalDateTime lastFailureAt;     // Last webhook failure
    }

    /**
     * Recent webhook events (last N events with details)
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentWebhookDTO {
        private String eventId;                  // Stripe event ID
        private String eventType;                // e.g., "customer.subscription.updated"
        private String status;                   // SUCCESS, FAILED, RETRY_PENDING
        private Integer retryCount;              // Number of retry attempts
        private String lastError;                // Last error message (if any)
        private LocalDateTime processedAt;       // When event was processed
        private LocalDateTime createdAt;         // When event was received
        private String tenantId;                 // Which tenant owns this event
        private String customerId;               // Stripe customer ID
    }

    /**
     * Failure analysis: breakdown of failure reasons
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FailureAnalysisDTO {
        private Long failureCount;               // Total failures
        private List<FailureReasonDTO> reasons;  // Breakdown by reason
        private LocalDateTime analysisTime;      // When analysis was generated
    }

    /**
     * Single failure reason with count
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FailureReasonDTO {
        private String reason;                   // e.g., "INVALID_EVENT", "TENANT_NOT_FOUND"
        private Long count;                      // How many failures of this type
        private Double percentage;               // % of all failures
    }

    /**
     * Circuit Breaker state and metrics
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CircuitBreakerStatusDTO {
        private String state;                    // CLOSED, OPEN, HALF_OPEN
        private Integer failureCount;            // Current failure count
        private Integer successCount;            // Current success count
        private Integer threshold;               // Failure threshold to open (10)
        private Integer timeoutSeconds;          // Timeout while OPEN (300s = 5min)
        private LocalDateTime lastStateChangeAt; // When state last changed
        private String message;                  // Human-readable CB status
    }

    /**
     * Builder helper for creating from metrics data
     */
    public static WebhookDashboardDTOBuilder withMetrics(
            Long totalReceived,
            Long successful,
            Long failed,
            Long pendingRetry,
            Double avgProcessingMs) {
        
        return builder()
                .metrics(WebhookMetricsDTO.builder()
                        .totalWebhooksReceived(totalReceived)
                        .totalSuccessful(successful)
                        .totalFailed(failed)
                        .totalPendingRetry(pendingRetry)
                        .successRate(calculateSuccessRate(successful, totalReceived))
                        .averageProcessingTimeMs(avgProcessingMs)
                        .build());
    }

    private static Double calculateSuccessRate(Long successful, Long total) {
        if (total == null || total == 0) return 100.0;
        return (successful * 100.0) / total;
    }
}
