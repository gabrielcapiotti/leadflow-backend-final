package com.leadflow.backend.service.billing;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.*;

/**
 * WebhookMetricsTracker - Centralized metrics collection for webhook system
 * 
 * Records business and technical metrics for:
 * - Event processing throughput
 * - Failure analysis
 * - Circuit breaker state
 * - Retry patterns
 * - Latency percentiles
 * - Database operations
 * - Alert lifecycle
 * 
 * All metrics are tagged with tenant_id for multi-tenant isolation
 * Compatible with Prometheus, Grafana, CloudWatch, etc.
 */
@Component
@Slf4j
public class WebhookMetricsTracker {

    private final MeterRegistry meterRegistry;

    // Gauges (tracked as mutable values)
    private final AtomicInteger queueSize = new AtomicInteger(0);
    private final Map<String, AtomicInteger> tenantQueueSizeMap = new java.util.concurrent.ConcurrentHashMap<>();
    private final AtomicInteger activeTenants = new AtomicInteger(0);

    // Counters (created on-demand, keyed by tags)
    private final Map<String, Counter> counterCache = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * Constructor - inject Spring's MeterRegistry
     */
    public WebhookMetricsTracker(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Initialize all meters and gauges
     * Called once at startup by WebhookMetricsConfig
     */
    public void initialize() {
        log.info("Initializing webhook metrics trackers");

        // Register Gauges (snapshot values)
        Gauge.builder("webhook.queue.size", queueSize::get)
                .description("Current number of webhook events in processing queue")
                .register(meterRegistry);

        Gauge.builder("webhook.active.tenants", activeTenants::get)
                .description("Number of active tenants with pending webhooks")
                .register(meterRegistry);

        log.info("Webhook metrics initialization complete");
    }

    // =====================================================
    // EVENT THROUGHPUT METRICS
    // =====================================================

    /**
     * Record incoming webhook event
     */
    public void recordEventReceived(UUID tenantId, String eventType, String eventId) {
        Counter.builder("webhook.events.received")
                .description("Total webhook events received")
                .tag("tenant_id", tenantId.toString())
                .tag("event_type", eventType)
                .register(meterRegistry)
                .increment();

        log.debug("Metric recorded: event_received tenant={} type={} id={}", 
                tenantId, eventType, eventId);
    }

    /**
     * Record successfully processed event
     */
    public void recordEventProcessed(UUID tenantId, String eventType, long durationMs) {
        // Counter for success
        Counter.builder("webhook.events.processed")
                .description("Total successfully processed webhook events")
                .tag("tenant_id", tenantId.toString())
                .tag("event_type", eventType)
                .register(meterRegistry)
                .increment();

        // Timer for latency
        Timer.builder("webhook.events.processing.time")
                .description("Time to process webhook events")
                .tag("tenant_id", tenantId.toString())
                .tag("status", "success")
                .tag("event_type", eventType)
                .register(meterRegistry)
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);

        log.debug("Metric recorded: event_processed tenant={} type={} duration={}ms", 
                tenantId, eventType, durationMs);
    }

    /**
     * Record failed event
     */
    public void recordEventFailed(UUID tenantId, String eventType, String reason, long durationMs) {
        // Counter for failure
        Counter.builder("webhook.events.failed")
                .description("Total failed webhook events")
                .tag("tenant_id", tenantId.toString())
                .tag("event_type", eventType)
                .tag("reason", reason)  // timeout, database, validation, network
                .register(meterRegistry)
                .increment();

        // Separate counter for failure reasons
        Counter.builder("webhook.failures.by.reason")
                .description("Failures categorized by reason")
                .tag("reason", reason)
                .tag("tenant_id", tenantId.toString())
                .register(meterRegistry)
                .increment();

        // Timer for failed attempts
        if (durationMs > 0) {
            Timer.builder("webhook.events.processing.time")
                    .description("Time to process webhook events")
                    .tag("tenant_id", tenantId.toString())
                    .tag("status", "failed")
                    .tag("event_type", eventType)
                    .register(meterRegistry)
                    .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
        }

        log.debug("Metric recorded: event_failed tenant={} type={} reason={}", 
                tenantId, eventType, reason);
    }

    // =====================================================
    // RETRY METRICS
    // =====================================================

    /**
     * Record retry attempt
     */
    public void recordRetryAttempt(UUID tenantId, String eventId, int retryCount, int maxRetries) {
        Counter.builder("webhook.retry.attempts")
                .description("Total retry attempts made")
                .tag("tenant_id", tenantId.toString())
                .register(meterRegistry)
                .increment();

        // Track retry count distribution
        DistributionSummary.builder("webhook.retry.count.distribution")
                .description("Distribution of retry counts per event")
                .tag("tenant_id", tenantId.toString())
                .register(meterRegistry)
                .record(retryCount);

        // Alert if excessive retries
        if (retryCount > maxRetries / 2) {
            Counter.builder("webhook.excessive.retries")
                    .description("Events exceeding retry threshold")
                    .tag("tenant_id", tenantId.toString())
                    .register(meterRegistry)
                    .increment();
        }

        log.debug("Metric recorded: retry_attempt tenant={} id={} count={}/{}", 
                tenantId, eventId, retryCount, maxRetries);
    }

    /**
     * Record retry success
     */
    public void recordRetrySuccess(UUID tenantId, long durationMs) {
        Counter.builder("webhook.retry.successes")
                .description("Retry attempts that succeeded")
                .tag("tenant_id", tenantId.toString())
                .register(meterRegistry)
                .increment();

        Timer.builder("webhook.retry.time")
                .description("Time spent on retry operations")
                .tag("tenant_id", tenantId.toString())
                .tag("status", "success")
                .register(meterRegistry)
                .record(durationMs, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    /**
     * Record retry failure
     */
    public void recordRetryFailure(UUID tenantId, String reason) {
        Counter.builder("webhook.retry.failures")
                .description("Retry attempts that failed")
                .tag("tenant_id", tenantId.toString())
                .tag("reason", reason)
                .register(meterRegistry)
                .increment();
    }

    // =====================================================
    // CIRCUIT BREAKER METRICS
    // =====================================================

    /**
     * Record circuit breaker state transition
     */
    public void recordCircuitBreakerTransition(
            CircuitBreakerConfig.CircuitState oldState,
            CircuitBreakerConfig.CircuitState newState) {

        Counter.builder("webhook.circuit.breaker.transitions")
                .description("Circuit breaker state changes")
                .tag("from_state", oldState.name())
                .tag("to_state", newState.name())
                .register(meterRegistry)
                .increment();

        log.info("Metric recorded: CB_transition {} → {}", oldState, newState);
    }

    /**
     * Record requests rejected by circuit breaker
     */
    public void recordCircuitBreakerRejection(UUID tenantId) {
        Counter.builder("webhook.circuit.breaker.rejected.requests")
                .description("Requests rejected due to circuit breaker OPEN state")
                .tag("tenant_id", tenantId.toString())
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record current circuit breaker state (as gauge)
     * State mapping: 0=CLOSED, 1=OPEN, 2=HALF_OPEN
     */
    public void recordCircuitBreakerState(CircuitBreakerConfig.CircuitState state) {
        int stateValue = state == CircuitBreakerConfig.CircuitState.CLOSED ? 0 :
                        state == CircuitBreakerConfig.CircuitState.OPEN ? 1 : 2;

        Gauge.builder("webhook.circuit.breaker.state", () -> stateValue)
                .description("Current circuit breaker state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)")
                .register(meterRegistry);
    }

    // =====================================================
    // QUEUE & PROCESSING METRICS
    // =====================================================

    /**
     * Update queue size gauge
     */
    public void setQueueSize(int size) {
        queueSize.set(size);
    }

    /**
     * Update tenant-specific queue size
     */
    public void setTenantQueueSize(UUID tenantId, int size) {
        AtomicInteger tenantSize = tenantQueueSizeMap.computeIfAbsent(tenantId.toString(), 
                k -> new AtomicInteger(0));
        tenantSize.set(size);

        Gauge.builder("webhook.tenant.queue.size", tenantSize::get)
                .tag("tenant_id", tenantId.toString())
                .register(meterRegistry);
    }

    /**
     * Update active tenants gauge
     */
    public void setActiveTenantCount(int count) {
        activeTenants.set(count);
    }

    // =====================================================
    // DATABASE METRICS
    // =====================================================

    /**
     * Record database operation success
     */
    public void recordDatabaseWrite(UUID tenantId, String operation) {
        Counter.builder("webhook.database.writes")
                .description("Successful database write operations")
                .tag("tenant_id", tenantId.toString())
                .tag("operation", operation)  // insert, update, save
                .register(meterRegistry)
                .increment();
    }

    /**
     * Record database operation failure
     */
    public void recordDatabaseError(UUID tenantId, String operation, String error) {
        Counter.builder("webhook.database.errors")
                .description("Database operation failures")
                .tag("tenant_id", tenantId.toString())
                .tag("operation", operation)
                .tag("error_type", error)  // connection, timeout, constraint_violation
                .register(meterRegistry)
                .increment();

        log.warn("Metric recorded: database_error tenant={} op={} err={}", 
                tenantId, operation, error);
    }

    // =====================================================
    // ALERT METRICS
    // =====================================================

    /**
     * Record alert creation
     */
    public void recordAlertCreated(UUID tenantId, String alertType, String severity) {
        Counter.builder("webhook.alerts.created")
                .description("Alerts created for webhook issues")
                .tag("tenant_id", tenantId.toString())
                .tag("alert_type", alertType)
                .tag("severity", severity)  // CRITICAL, WARNING, INFO
                .register(meterRegistry)
                .increment();

        // Track critical alerts separately for visibility
        if ("CRITICAL".equals(severity)) {
            Counter.builder("webhook.alerts.critical")
                    .description("Critical severity alerts")
                    .tag("tenant_id", tenantId.toString())
                    .register(meterRegistry)
                    .increment();
        }
    }

    /**
     * Record alert resolution
     */
    public void recordAlertResolved(UUID tenantId, String alertType, long activeMinutes) {
        Counter.builder("webhook.alerts.resolved")
                .description("Alerts resolved/acknowledged")
                .tag("tenant_id", tenantId.toString())
                .tag("alert_type", alertType)
                .register(meterRegistry)
                .increment();

        // Track how long alerts were active
        DistributionSummary.builder("webhook.alert.active.duration.minutes")
                .description("How long alerts remained active")
                .tag("alert_type", alertType)
                .register(meterRegistry)
                .record(activeMinutes);
    }

    // =====================================================
    // SUMMARY METRICS (For Dashboard)
    // =====================================================

    /**
     * Calculate current success rate
     */
    public double getSuccessRate() {
        // Query meterRegistry for counters
        // This would typically be done externally via Prometheus queries
        // For now, return 0 as placeholder
        return 0.0;
    }

    /**
     * Get all current metric values
     */
    public Map<String, Object> getCurrentMetrics() {
        Map<String, Object> metrics = new HashMap<>();
        
        metrics.put("queue_size", queueSize.get());
        metrics.put("active_tenants", activeTenants.get());
        
        return metrics;
    }
}
