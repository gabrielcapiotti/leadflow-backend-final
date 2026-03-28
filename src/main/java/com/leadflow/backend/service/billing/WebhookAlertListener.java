package com.leadflow.backend.service.billing;

import com.leadflow.backend.multitenancy.context.TenantContext;
import com.leadflow.backend.entities.StripeEventLog;
import com.leadflow.backend.entities.WebhookAlertEvent;
import com.leadflow.backend.repository.StripeEventLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.*;

/**
 * WebhookAlertListener - Scheduled checks for webhook system health
 * Monitors conditions and triggers alerts via WebhookAlertService
 * Runs every 5 minutes
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class WebhookAlertListener {

    private final WebhookAlertService alertService;
    private final StripeEventLogRepository eventRepository;
    private final CircuitBreakerConfig circuitBreaker;
    private final WebhookMetricsTracker metricsTracker;

    // Track last known state to avoid repeated alerts
    private Map<String, LocalDateTime> lastStateChangeMap = new HashMap<>();

    /**
     * Main scheduled health check - runs every 5 minutes
     */
    @Scheduled(fixedRate = 300000)  // 5 minutes
    public void checkWebhookHealth() {
        log.debug("Starting webhook health check");

        try {
            // Get all unique tenants with active webhooks
            List<UUID> activeTenants = eventRepository.findDistinctTenantsWithRecentEvents(
                    LocalDateTime.now().minusHours(1)
            );

            if (activeTenants.isEmpty()) {
                log.debug("No active tenants found in last hour, skipping health checks");
                return;
            }

            for (UUID tenantId : activeTenants) {
                try {
                    if (tenantId == null) {
                        log.warn("Skipping health check for null tenant");
                        continue;
                    }
                    TenantContext.setTenant(tenantId.toString());
                    checkTenantHealth(tenantId);
                } catch (Exception e) {
                    log.error("Error checking health for tenant {}", tenantId, e);
                } finally {
                    TenantContext.clear();
                }
            }

            log.debug("Webhook health check completed for {} tenants", activeTenants.size());

        } catch (Exception e) {
            log.error("Critical error in webhook health check", e);
            // Don't throw - allow scheduler to continue running
        }
    }

    /**
     * Check health for a single tenant
     */
    private void checkTenantHealth(UUID tenantId) {
        // Check 1: Circuit Breaker Status
        checkCircuitBreakerHealth(tenantId);

        // Check 2: Failure Rate
        checkFailureRateHealth(tenantId);

        // Check 3: Processing Stalled
        checkProcessingHealth(tenantId);

        // Check 4: Excessive Retries
        checkRetryHealth(tenantId);

        // Check 5: Latency
        checkLatencyHealth(tenantId);
    }

    /**
     * Check 1: Circuit breaker status
     */
    private void checkCircuitBreakerHealth(UUID tenantId) {
        try {
            if (circuitBreaker.getState() == CircuitBreakerConfig.CircuitState.OPEN) {
                String stateKey = "CB_OPEN_" + tenantId;
                LocalDateTime lastChange = lastStateChangeMap.get(stateKey);

                // Only alert once per state change
                if (lastChange == null || lastChange.isBefore(LocalDateTime.now().minusMinutes(30))) {
                    alertService.checkCircuitBreakerStatus(circuitBreaker, tenantId);
                    metricsTracker.recordAlertCreated(tenantId, "CIRCUIT_BREAKER_OPEN", "CRITICAL");
                    lastStateChangeMap.put(stateKey, LocalDateTime.now());
                }
            }
        } catch (Exception e) {
            log.error("Error checking circuit breaker health", e);
        }
    }

    /**
     * Check 2: Failure rate (failures in last 5 minutes)
     */
    private void checkFailureRateHealth(UUID tenantId) {
        try {
            LocalDateTime fiveMinutesAgo = LocalDateTime.now().minusMinutes(5);

            long totalEvents = eventRepository.countByTenantIdAndCreatedAtAfter(tenantId, fiveMinutesAgo);
            long failedEvents = eventRepository.countFailedByTenantIdAndCreatedAtAfter(tenantId, fiveMinutesAgo);

            if (totalEvents > 0) {
                alertService.checkFailureRate(tenantId, failedEvents, totalEvents);
                metricsTracker.recordAlertCreated(tenantId, "HIGH_FAILURE_RATE", "WARNING");
            }
        } catch (Exception e) {
            log.error("Error checking failure rate", e);
        }
    }

    /**
     * Check 3: Processing stalled (no events processed in 10 min)
     */
    private void checkProcessingHealth(UUID tenantId) {
        try {
            LocalDateTime tenMinutesAgo = LocalDateTime.now().minusMinutes(10);
            Optional<StripeEventLog> lastEvent = eventRepository.findLastProcessedByTenant(tenantId);

            if (lastEvent.isPresent()) {
                StripeEventLog event = lastEvent.get();
                if (event.getProcessedAt() != null && 
                    event.getProcessedAt().isBefore(tenMinutesAgo)) {
                    
                    alertService.checkProcessingStalled(tenantId, event.getProcessedAt());
                    metricsTracker.recordAlertCreated(tenantId, "PROCESSING_STALLED", "WARNING");
                }
            } else {
                // No events found for this tenant - could indicate stalled processing
                LocalDateTime tenantCreatedTime = LocalDateTime.now().minusHours(24);
                long recentEventCount = eventRepository.countByTenantIdAndCreatedAtAfter(
                        tenantId, 
                        tenantCreatedTime
                );
                
                if (recentEventCount == 0) {
                    log.debug("No events found for tenant {} in last 24 hours", tenantId);
                }
            }
        } catch (Exception e) {
            log.error("Error checking processing health", e);
        }
    }

    /**
     * Check 4: Excessive retries (events with > 5 retries)
     */
    private void checkRetryHealth(UUID tenantId) {
        try {
            List<StripeEventLog> excessiveRetryEvents = eventRepository.findExcessiveRetryEvents(
                    tenantId, 
                    5,  // threshold: 5+ retries
                    LocalDateTime.now().minusHours(1)
            );

            for (StripeEventLog event : excessiveRetryEvents) {
                alertService.checkExcessiveRetries(
                        tenantId,
                        event.getEventId(),
                        event.getRetryCount(),
                        10  // max retries
                );
                metricsTracker.recordAlertCreated(tenantId, "EXCESSIVE_RETRIES", "WARNING");
            }
        } catch (Exception e) {
            log.error("Error checking retry health", e);
        }
    }

    /**
     * Check 5: Latency (average processing time)
     */
    private void checkLatencyHealth(UUID tenantId) {
        try {
            Double avgLatency = eventRepository.getAverageProcessingTimeMs(
                    tenantId,
                    LocalDateTime.now().minusMinutes(5)
            );

            if (avgLatency != null && avgLatency > 0) {
                alertService.checkLatency(tenantId, avgLatency);
            }
        } catch (Exception e) {
            log.error("Error checking latency", e);
        }
    }

    /**
     * Resolve alerts when conditions normalize
     * Called after detecting improvement
     */
    public void resolveAlertIfHealthy(UUID tenantId, WebhookAlertEvent.AlertType alertType) {
        try {
            // Attempt to resolve alerts of this type for the tenant
            alertService.resolveAlertsByType(tenantId, alertType);
        } catch (Exception e) {
            log.error("Error resolving alert", e);
        }
    }

    /**
     * Optional: Listen for webhook processing completion events
     * Can be called from StripeWebhookController for real-time checks
     */
    public void onWebhookProcessed(StripeEventLog event) {
        if (event.getTenantId() == null) {
            return;
        }

        try {
            TenantContext.setTenant(event.getTenantId().toString());

            // Quick checks on fresh event
            if (event.getRetryCount() != null && event.getRetryCount() >= 5) {
                alertService.checkExcessiveRetries(
                        event.getTenantId(),
                        event.getEventId(),
                        event.getRetryCount(),
                        10
                );
            }
        } catch (Exception e) {
            log.error("Error processing webhook alert event", e);
        } finally {
            TenantContext.clear();
        }
    }
}
