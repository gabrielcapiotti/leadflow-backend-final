package com.leadflow.backend.dto.metrics;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.Map;

/**
 * WebhookEventMetrics - DTO for webhook system metrics and analytics
 * 
 * Contains aggregated metrics for:
 * - Throughput (events received, processed, failed)
 * - Success/failure rates
 * - Latencies (avg, P50, P95, P99)
 * - Failure breakdown by category
 * - Retry statistics
 * - Circuit breaker status
 * - Queue depth
 * 
 * Used by WebhookMetricsController to expose metrics via REST API
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class WebhookEventMetrics {

    // =====================================================
    // THROUGHPUT METRICS
    // =====================================================

    /** Total number of events received */
    private long totalReceived;

    /** Number of events successfully processed */
    private long totalProcessed;

    /** Number of events that failed processing */
    private long totalFailed;

    /** Success rate as percentage (0-100) */
    private double successRate;

    /** Failure rate as percentage (0-100) */
    private double failureRate;

    // =====================================================
    // LATENCY METRICS (Percentiles)
    // =====================================================

    /** Average processing time in milliseconds */
    private double avgProcessingTimeMs;

    /** Median processing time (P50) in milliseconds */
    private double p50LatencyMs;

    /** 75th percentile latency in milliseconds */
    private double p75LatencyMs;

    /** 95th percentile latency in milliseconds */
    private double p95LatencyMs;

    /** 99th percentile latency in milliseconds */
    private double p99LatencyMs;

    // =====================================================
    // FAILURE ANALYSIS
    // =====================================================

    /** Breakdown of failures by reason (timeout, database, validation, etc.) */
    private Map<String, Long> failuresByReason;

    /** Breakdown of failures by event type */
    private Map<String, Long> failuresByEventType;

    /** Most recent failure timestamp */
    private LocalDateTime lastFailureAt;

    // =====================================================
    // RETRY STATISTICS
    // =====================================================

    /** Total retry attempts made */
    private long totalRetries;

    /** Number of retries that eventually succeeded */
    private long successfulRetries;

    /** Percentage of retries that succeeded */
    private double retrySuccessRate;

    /** Maximum number of retries needed for any single event */
    private int maxRetryCount;

    /** Average retry count across all retried events */
    private double avgRetryCount;

    // =====================================================
    // CIRCUIT BREAKER STATUS
    // =====================================================

    /** Current circuit breaker state (CLOSED, OPEN, HALF_OPEN) */
    private String circuitBreakerState;

    /** Number of times circuit breaker transitioned states */
    private long circuitBreakerTransitions;

    /** Number of requests rejected due to circuit breaker being OPEN */
    private long circuitBreakerRejectedRequests;

    /** Time when circuit breaker last opened */
    private LocalDateTime lastCircuitBreakerOpenAt;

    // =====================================================
    // QUEUE METRICS
    // =====================================================

    /** Current number of events in processing queue */
    private int queueSize;

    /** Maximum queue size observed */
    private int maxQueueSize;

    /** Average queue size */
    private double avgQueueSize;

    // =====================================================
    // TEMPORAL METRICS
    // =====================================================

    /** Number of active tenants with pending webhooks */
    private int activeTenants;

    /** Time period this metrics snapshot covers (in seconds) */
    private long aggregationPeriodSeconds;

    /** Timestamp when metrics were captured */
    private LocalDateTime capturedAt;

    // =====================================================
    // ALERT METRICS
    // =====================================================

    /** Number of active (unresolved) alerts */
    private int activeAlerts;

    /** Number of critical severity alerts */
    private int criticalAlerts;

    /** Number of warning severity alerts */
    private int warningAlerts;

    /** Total alerts created in this period */
    private long totalAlertsCreated;

    /** Breakdown of alerts by type */
    private Map<String, Long> alertsByType;

    // =====================================================
    // BUILDER HELPERS
    // =====================================================

    /**
     * Calculate success rate from processed/received
     */
    public void calculateSuccessRate() {
        if (totalReceived > 0) {
            this.successRate = (totalProcessed * 100.0) / totalReceived;
            this.failureRate = 100.0 - this.successRate;
        }
    }

    /**
     * Calculate retry success rate
     */
    public void calculateRetrySuccessRate() {
        if (totalRetries > 0) {
            this.retrySuccessRate = (successfulRetries * 100.0) / totalRetries;
        }
    }

    /**
     * Nested DTO for Tenant-specific metrics
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TenantMetrics {
        private String tenantId;
        private long receivedCount;
        private long processedCount;
        private long failedCount;
        private double successRate;
        private double avgLatencyMs;
        private int queueSize;
        private Map<String, Long> failuresByReason;
        private LocalDateTime capturedAt;
    }

    /**
     * Nested DTO for Failure Breakdown
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FailureBreakdown {
        private Map<String, Long> byReason;      // timeout → 42, database → 15, validation → 8
        private Map<String, Long> byEventType;   // charge.succeeded → 30, customer.created → 35
        private Map<String, Long> byTenant;      // tenant_id → count
        private long totalFailures;
        private LocalDateTime periodStart;
        private LocalDateTime periodEnd;
    }

    /**
     * Nested DTO for Latency Percentiles
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class LatencyPercentiles {
        private double avg;
        private double p50;
        private double p75;
        private double p95;
        private double p99;
        private double max;
        private int sampleCount;
        private LocalDateTime calculatedAt;
    }

    /**
     * Nested DTO for Circuit Breaker Status
     */
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class CircuitBreakerStatus {
        private String state;                    // CLOSED, OPEN, HALF_OPEN
        private long failureCount;
        private int failureThreshold;
        private long transitionCount;
        private long rejectedRequests;
        private LocalDateTime lastStateChange;
        private LocalDateTime lastOpenedAt;
        private long secondsUntilHalfOpen;
    }
}
