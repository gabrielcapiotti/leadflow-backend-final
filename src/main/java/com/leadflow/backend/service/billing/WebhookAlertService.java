package com.leadflow.backend.service.billing;

import com.leadflow.backend.dto.billing.WebhookAlertDTO;
import com.leadflow.backend.entities.WebhookAlertEvent;
import com.leadflow.backend.repository.WebhookAlertRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Slf4j
@RequiredArgsConstructor
public class WebhookAlertService {

    private final WebhookAlertRepository alertRepository;

    private static final int DEDUP_THRESHOLD_MINUTES = 1;

    // ================= ALERT CORE =================

    @Transactional
    public WebhookAlertEvent createAlert(
            WebhookAlertEvent.AlertType alertType,
            WebhookAlertEvent.AlertSeverity severity,
            UUID tenantId,
            String message,
            Map<String, Object> metrics) {

        LocalDateTime threshold = LocalDateTime.now().minusMinutes(DEDUP_THRESHOLD_MINUTES);

        List<WebhookAlertEvent> existing = alertRepository
                .findRecentUnresolvedSameType(tenantId, alertType, threshold);

        if (!existing.isEmpty()) {
            return existing.get(0);
        }

        WebhookAlertEvent alert = WebhookAlertEvent.builder()
                .alertType(alertType)
                .severity(severity)
                .tenantId(tenantId)
                .message(message)
                .metrics(metrics != null ? metrics : new HashMap<>())
                .build();

        return alertRepository.save(alert);
    }

    @Transactional
    public WebhookAlertEvent resolveAlert(UUID alertId) {
        return alertRepository.findById(alertId)
                .map(alert -> {
                    if (alert.isActive()) {
                        alert.resolve();
                        return alertRepository.save(alert);
                    }
                    return alert;
                })
                .orElse(null);
    }

    @Transactional
    public int resolveAlertsByType(UUID tenantId, WebhookAlertEvent.AlertType type) {
        List<WebhookAlertEvent> alerts = alertRepository
                .findRecentUnresolvedSameType(tenantId, type, LocalDateTime.now().minusHours(24));

        int count = 0;
        for (WebhookAlertEvent alert : alerts) {
            if (alert.isActive()) {
                alert.resolve();
                alertRepository.save(alert);
                count++;
            }
        }
        return count;
    }

    // ================= ALERT CHECKS =================

    public void checkFailureRate(UUID tenantId, long failed, long processed) {
        if (processed == 0) return;

        double rate = (failed * 100.0) / processed;

        if (rate > 50.0) {
            createAlert(
                    WebhookAlertEvent.AlertType.HIGH_FAILURE_RATE,
                    WebhookAlertEvent.AlertSeverity.CRITICAL,
                    tenantId,
                    "Failure rate high: " + rate + "%",
                    Map.of("rate", rate)
            );
        }
    }

    public void checkProcessingStalled(UUID tenantId, LocalDateTime lastProcessed) {
        if (lastProcessed == null) return;

        long minutes = ChronoUnit.MINUTES.between(lastProcessed, LocalDateTime.now());

        if (minutes > 10) {
            createAlert(
                    WebhookAlertEvent.AlertType.PROCESSING_STALLED,
                    WebhookAlertEvent.AlertSeverity.INFO,
                    tenantId,
                    "Processing stalled: " + minutes + " minutes",
                    Map.of("minutes", minutes)
            );
        }
    }

    // ================= READ =================

    public List<WebhookAlertEvent> getActiveAlerts() {
        return alertRepository.findActiveAlerts();
    }

    public List<WebhookAlertEvent> getActiveAlertsByTenant(UUID tenantId) {
        return alertRepository.findActivAlertsByTenant(tenantId);
    }

    public List<WebhookAlertEvent> getCriticalAlerts() {
        return alertRepository.findCriticalUnresolved();
    }

    public Page<WebhookAlertEvent> getAlertHistory(UUID tenantId, int hours, Pageable pageable) {
        return alertRepository.findHistoryByTenant(
                tenantId,
                LocalDateTime.now().minusHours(hours),
                pageable
        );
    }

    public List<WebhookAlertEvent> getAlertHistory(UUID tenantId, int hours) {
        return alertRepository.findRecentByTenant(
                tenantId,
                LocalDateTime.now().minusHours(hours)
        );
    }

    public List<WebhookAlertEvent> getAlertsByType(WebhookAlertEvent.AlertType type) {
        return alertRepository.findUnresolvedByType(type);
    }

    public List<WebhookAlertEvent> getAlertsBySeverity(WebhookAlertEvent.AlertSeverity severity) {
        return alertRepository.findUnresolvedBySeverity(severity);
    }

    // ================= PARSERS =================

    public WebhookAlertEvent.AlertType parseAlertType(String value) {
        return Arrays.stream(WebhookAlertEvent.AlertType.values())
                .filter(v -> v.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid alert type: " + value));
    }

    public WebhookAlertEvent.AlertSeverity parseSeverity(String value) {
        return Arrays.stream(WebhookAlertEvent.AlertSeverity.values())
                .filter(v -> v.name().equalsIgnoreCase(value))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid severity: " + value));
    }

    // ================= DTO =================

    public List<WebhookAlertDTO> toDTO(List<WebhookAlertEvent> list) {
        return list.stream().map(WebhookAlertDTO::fromEntity).toList();
    }

    public Page<WebhookAlertDTO> toDTO(Page<WebhookAlertEvent> page) {
        return page.map(WebhookAlertDTO::fromEntity);
    }

    // ================= STATS =================

    public WebhookAlertDTO.AlertStatsDTO getAlertStats() {

        List<WebhookAlertEvent> active = getActiveAlerts();

        long critical = active.stream().filter(a -> a.getSeverity() == WebhookAlertEvent.AlertSeverity.CRITICAL).count();
        long warning = active.stream().filter(a -> a.getSeverity() == WebhookAlertEvent.AlertSeverity.WARNING).count();
        long info = active.stream().filter(a -> a.getSeverity() == WebhookAlertEvent.AlertSeverity.INFO).count();

        Map<String, Long> distribution = active.stream()
                .collect(Collectors.groupingBy(a -> a.getAlertType().name(), Collectors.counting()));

        LocalDateTime last = active.stream()
                .map(WebhookAlertEvent::getCreatedAt)
                .max(LocalDateTime::compareTo)
                .orElse(null);

        return WebhookAlertDTO.AlertStatsDTO.builder()
                .totalActive((long) active.size())
                .criticalCount(critical)
                .warningCount(warning)
                .infoCount(info)
                .alertTypeDistribution(distribution)
                .lastAlertAt(last)
                .build();
    }

    // ================= ALERT HELPERS =================

    public void checkCircuitBreakerStatus(CircuitBreakerConfig circuitBreaker, UUID tenantId) {
        createAlert(
                WebhookAlertEvent.AlertType.CIRCUIT_BREAKER_OPENED,
                WebhookAlertEvent.AlertSeverity.CRITICAL,
                tenantId,
                "Circuit breaker is OPEN for vendor",
                Map.of("state", circuitBreaker.getState().toString())
        );
    }

    public void checkExcessiveRetries(UUID tenantId, String eventId, Integer retryCount, int maxRetries) {
        if (retryCount == null) return;
        if (retryCount >= maxRetries) {
            createAlert(
                    WebhookAlertEvent.AlertType.EXCESSIVE_RETRIES,
                    WebhookAlertEvent.AlertSeverity.WARNING,
                    tenantId,
                    "Excessive retries detected for event: " + eventId,
                    Map.of("eventId", eventId, "retryCount", retryCount, "maxRetries", maxRetries)
            );
        }
    }

    public void checkLatency(UUID tenantId, Double avgLatencyMs) {
        if (avgLatencyMs == null) return;
        if (avgLatencyMs > 1000) { // threshold: > 1 second
            createAlert(
                    WebhookAlertEvent.AlertType.TIMEOUT_DETECTED,
                    WebhookAlertEvent.AlertSeverity.WARNING,
                    tenantId,
                    "High latency detected: " + String.format("%.2f", avgLatencyMs) + "ms",
                    Map.of("avgLatencyMs", avgLatencyMs)
            );
        }
    }

    public void createDatabaseErrorAlert(UUID tenantId, String errorMessage) {
        createAlert(
                WebhookAlertEvent.AlertType.DATABASE_ERROR,
                WebhookAlertEvent.AlertSeverity.CRITICAL,
                tenantId,
                "Database error: " + errorMessage,
                Map.of("error", errorMessage)
        );
    }
}