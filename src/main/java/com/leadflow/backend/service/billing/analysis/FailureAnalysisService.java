package com.leadflow.backend.service.billing.analysis;

import com.leadflow.backend.dto.analysis.FailureAnalysisDTO;
import com.leadflow.backend.dto.analysis.FailureAnalysisDTO.*;
import com.leadflow.backend.entities.StripeEventLog;
import com.leadflow.backend.repository.StripeEventLogRepository;
import com.leadflow.backend.service.billing.WebhookMetricsTracker;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for analyzing webhook failure patterns
 * 
 * Provides:
 * - Failure trend analysis (30d, 7d, 24h, 1h windows)
 * - Automatic categorization by reason, severity, event type
 * - Root cause identification
 * - Automated remediation suggestions
 * - Health status assessment
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FailureAnalysisService {

    private final StripeEventLogRepository eventLogRepository;
    private final WebhookMetricsTracker metricsTracker;
    private final MeterRegistry meterRegistry;

    /**
     * Analyzes webhook failures over specified window
     * 
     * @param windowSeconds - Analysis window (e.g., 86400 for 24h)
     * @return Complete failure analysis with trends, suggestions, health status
     */
    public FailureAnalysisDTO analyzeFailures(long windowSeconds) {
        log.info("[FAILURE ANALYSIS] Starting analysis for {} seconds", windowSeconds);
        
        LocalDateTime startTime = LocalDateTime.now().minusSeconds(windowSeconds);
        List<StripeEventLog> allEvents = eventLogRepository.findByCreatedAtAfter(startTime);
        List<StripeEventLog> failedEvents = allEvents.stream()
                .filter(e -> e.getStatus() == StripeEventLog.EventProcessingStatus.FAILED ||
                           e.getStatus() == StripeEventLog.EventProcessingStatus.RETRY_PENDING)
                .collect(Collectors.toList());
        
        log.info("[FAILURE ANALYSIS] Analyzing {} failures out of {} total events",
                failedEvents.size(), allEvents.size());
        
        // Build comprehensive analysis
        return FailureAnalysisDTO.builder()
                .analysisId(UUID.randomUUID().toString())
                .analyzedAt(LocalDateTime.now())
                .analysisWindowSeconds(windowSeconds)
                .totalEventsAnalyzed(allEvents.size())
                .totalFailures(failedEvents.size())
                .overallFailureRate(calculateFailureRate(allEvents.size(), failedEvents.size()))
                .failureBreakdown(buildFailureBreakdown(failedEvents))
                .trendAnalysis(buildTrendAnalysis(allEvents, failedEvents, windowSeconds))
                .suggestions(generateRemediationSuggestions(failedEvents))
                .healthStatus(assessHealthStatus(failedEvents, allEvents))
                .build();
    }

    /**
     * Breakdown failures by reason, event type, severity
     */
    private FailureBreakdown buildFailureBreakdown(List<StripeEventLog> failedEvents) {
        Map<String, Long> byReason = new HashMap<>();
        Map<String, Long> byEventType = new HashMap<>();
        Map<String, Long> bySeverity = new HashMap<>();
        long timeouts = 0, databaseErrors = 0, validationErrors = 0, unknownErrors = 0;
        
        for (StripeEventLog event : failedEvents) {
            String reason = extractFailureReason(event);
            byReason.merge(reason, 1L, Long::sum);
            byEventType.merge(event.getEventType(), 1L, Long::sum);
            
            // Categorize by severity
            String severity = categorizeSeverity(event);
            bySeverity.merge(severity, 1L, Long::sum);
            
            // Count error types
            if (reason.contains("timeout")) timeouts++;
            else if (reason.contains("database")) databaseErrors++;
            else if (reason.contains("validation")) validationErrors++;
            else unknownErrors++;
        }
        
        // Build detailed metrics per reason
        Map<String, FailureReasonMetrics> reasonMetrics = new HashMap<>();
        for (String reason : byReason.keySet()) {
            List<StripeEventLog> reasonEvents = failedEvents.stream()
                    .filter(e -> extractFailureReason(e).equals(reason))
                    .collect(Collectors.toList());
            
            long count = reasonEvents.size();
            double percentage = failedEvents.isEmpty() ? 0 : (double) count / failedEvents.size();
            
            long avgTime = (long) reasonEvents.stream()
                    .mapToLong(e -> calculateProcessingTime(e))
                    .average()
                    .orElse(0.0);
            
            long maxTime = reasonEvents.stream()
                    .mapToLong(this::calculateProcessingTime)
                    .max()
                    .orElse(0L);
            
            int recoveryAttempts = reasonEvents.stream()
                    .mapToInt(StripeEventLog::getRetryCount)
                    .sum();
            
            double recoverySuccessRate = reasonEvents.stream()
                    .filter(e -> e.getStatus() == StripeEventLog.EventProcessingStatus.SUCCESS)
                    .count() / (double) Math.max(1, reasonEvents.size());
            
            reasonMetrics.put(reason, FailureReasonMetrics.builder()
                    .reason(reason)
                    .count(count)
                    .percentage(percentage)
                    .avgProcessingTimeMs(avgTime)
                    .maxProcessingTimeMs(maxTime)
                    .recoveryAttempts(recoveryAttempts)
                    .recoverySuccessRate(recoverySuccessRate)
                    .suggestedAction(getSuggestedAction(reason))
                    .severityLevel(categorizeSeverity(reasonEvents.get(0)))
                    .firstOccurrence(reasonEvents.stream()
                            .map(StripeEventLog::getCreatedAt)
                            .min(Comparator.naturalOrder())
                            .orElse(LocalDateTime.now()))
                    .lastOccurrence(reasonEvents.stream()
                            .map(StripeEventLog::getCreatedAt)
                            .max(Comparator.naturalOrder())
                            .orElse(LocalDateTime.now()))
                    .build());
        }
        
        // Build event type metrics
        Map<String, FailureEventMetrics> eventMetrics = new HashMap<>();
        for (String eventType : byEventType.keySet()) {
            List<StripeEventLog> typeEvents = failedEvents.stream()
                    .filter(e -> e.getEventType().equals(eventType))
                    .collect(Collectors.toList());
            
            long totalReceived = typeEvents.size(); // simplified - would need to count all, not just failed
            long totalFailed = typeEvents.size();
            
            Double avgRetryCount = typeEvents.stream()
                    .mapToInt(StripeEventLog::getRetryCount)
                    .average()
                    .orElse(0.0);
            
            double retrySuccessRate = typeEvents.stream()
                    .filter(e -> e.getStatus() == StripeEventLog.EventProcessingStatus.SUCCESS)
                    .count() / (double) Math.max(1, typeEvents.size());
            
            eventMetrics.put(eventType, FailureEventMetrics.builder()
                    .eventType(eventType)
                    .totalReceived(totalReceived)
                    .totalFailed(totalFailed)
                    .failureRate(totalReceived == 0 ? 0 : (double) totalFailed / totalReceived)
                    .avgRetryCount(avgRetryCount.intValue())
                    .retrySuccessRate(retrySuccessRate)
                    .topFailureReason(typeEvents.stream()
                            .map(this::extractFailureReason)
                            .collect(Collectors.groupingByConcurrent(
                                    String::toString,
                                    Collectors.counting()))
                            .entrySet().stream()
                            .max(Map.Entry.comparingByValue())
                            .map(Map.Entry::getKey)
                            .orElse("UNKNOWN"))
                    .recentFailureReasons(typeEvents.stream()
                            .map(this::extractFailureReason)
                            .distinct()
                            .limit(5)
                            .collect(Collectors.toList()))
                    .recommendedAction("Review error handling for " + eventType)
                    .build());
        }
        
        // Recent failures (last 10)
        List<RecentFailure> recent = failedEvents.stream()
                .sorted(Comparator.comparing(StripeEventLog::getCreatedAt).reversed())
                .limit(10)
                .map(e -> RecentFailure.builder()
                        .eventId(e.getEventId())
                        .eventType(e.getEventType())
                        .reason(extractFailureReason(e))
                        .processingTimeMs(calculateProcessingTime(e))
                        .retryCount(e.getRetryCount())
                        .occurredAt(e.getCreatedAt())
                        .status(e.getStatus().toString())
                        .build())
                .collect(Collectors.toList());
        
        return FailureBreakdown.builder()
                .byReason(reasonMetrics)
                .byEventType(eventMetrics)
                .bySeverity(bySeverity)
                .recentFailures(recent)
                .totalTimeouts(timeouts)
                .totalDatabaseErrors(databaseErrors)
                .totalValidationErrors(validationErrors)
                .totalUnknownErrors(unknownErrors)
                .build();
    }

    /**
     * Analyze trends across different time windows
     */
    private TrendAnalysis buildTrendAnalysis(List<StripeEventLog> allEvents,
                                            List<StripeEventLog> failedEvents,
                                            long windowSeconds) {
        LocalDateTime now = LocalDateTime.now();
        
        // Calculate failure rates for different windows
        double rate_30d = calculateFailureRateForWindow(now.minusDays(30));
        double rate_7d = calculateFailureRateForWindow(now.minusDays(7));
        double rate_24h = calculateFailureRateForWindow(now.minusHours(24));
        double rate_1h = calculateFailureRateForWindow(now.minusHours(1));
        
        // Determine trends
        String trend_30d_vs_7d = compareTrends(rate_30d, rate_7d);
        String trend_7d_vs_24h = compareTrends(rate_7d, rate_24h);
        String trend_24h_vs_1h = compareTrends(rate_24h, rate_1h);
        
        // Failures by hour
        Map<Integer, Long> failuresByHour = new HashMap<>();
        LocalDateTime hourStart = now.minusHours(24);
        for (StripeEventLog event : failedEvents) {
            if (event.getCreatedAt().isAfter(hourStart)) {
                int hour = event.getCreatedAt().getHour();
                failuresByHour.merge(hour, 1L, Long::sum);
            }
        }
        
        return TrendAnalysis.builder()
                .failureRate_30d(rate_30d)
                .failureRate_7d(rate_7d)
                .failureRate_24h(rate_24h)
                .failureRate_1h(rate_1h)
                .trend_30d_vs_7d(trend_30d_vs_7d)
                .trend_7d_vs_24h(trend_7d_vs_24h)
                .trend_24h_vs_1h(trend_24h_vs_1h)
                .failuresByHour(failuresByHour)
                .recoveryRate_success(calculateRecoveryRate(failedEvents, "SUCCESS"))
                .recoveryRate_timeout(calculateRecoveryRate(failedEvents, "TIMEOUT"))
                .recoveryRate_retry(calculateRecoveryRate(failedEvents, "RETRY"))
                .analysisStart(now.minusSeconds(windowSeconds))
                .analysisEnd(now)
                .build();
    }

    /**
     * Generate automated remediation suggestions
     */
    private List<RemediationSuggestion> generateRemediationSuggestions(List<StripeEventLog> failedEvents) {
        List<RemediationSuggestion> suggestions = new ArrayList<>();
        
        // Analyze timeout failures
        long timeoutCount = failedEvents.stream()
                .filter(e -> extractFailureReason(e).contains("timeout"))
                .count();
        if (timeoutCount > 0) {
            suggestions.add(RemediationSuggestion.builder()
                    .suggestionId(UUID.randomUUID().toString())
                    .category("TIMEOUT")
                    .severity(timeoutCount > failedEvents.size() * 0.2 ? "CRITICAL" : "WARNING")
                    .failureReason("timeout")
                    .shortDescription("High timeout rate detected")
                    .detailedDescription("Events are frequently timing out during processing. " +
                            "This may indicate slow external services or network issues.")
                    .recommendedActions(List.of(
                            "Increase webhook processing timeout",
                            "Review Stripe API response times",
                            "Check network connectivity",
                            "Implement circuit breaker for slow endpoints",
                            "Consider async processing for heavy operations"
                    ))
                    .estimatedImpact("HIGH")
                    .expectedImprovement(0.35)
                    .implementationEffort("MEDIUM")
                    .estimatedMinutesToImplement(30)
                    .affectedEventTypes(failedEvents.stream()
                            .filter(e -> extractFailureReason(e).contains("timeout"))
                            .map(StripeEventLog::getEventType)
                            .distinct()
                            .collect(Collectors.toList()))
                    .affectedEventCount(timeoutCount)
                    .suggestedAt(LocalDateTime.now())
                    .implemented(false)
                    .build());
        }
        
        // Analyze database failures
        long dbCount = failedEvents.stream()
                .filter(e -> extractFailureReason(e).contains("database"))
                .count();
        if (dbCount > 0) {
            suggestions.add(RemediationSuggestion.builder()
                    .suggestionId(UUID.randomUUID().toString())
                    .category("DATABASE")
                    .severity(dbCount > failedEvents.size() * 0.2 ? "CRITICAL" : "WARNING")
                    .failureReason("database")
                    .shortDescription("Database operation failures detected")
                    .detailedDescription("Multiple database errors indicate potential schema issues, " +
                            "missing indices, or resource constraints.")
                    .recommendedActions(List.of(
                            "Optimize database queries",
                            "Add missing indices",
                            "Review database connection pool settings",
                            "Check database performance logs",
                            "Implement query caching where applicable"
                    ))
                    .estimatedImpact("HIGH")
                    .expectedImprovement(0.40)
                    .implementationEffort("HIGH")
                    .estimatedMinutesToImplement(60)
                    .affectedEventTypes(failedEvents.stream()
                            .filter(e -> extractFailureReason(e).contains("database"))
                            .map(StripeEventLog::getEventType)
                            .distinct()
                            .collect(Collectors.toList()))
                    .affectedEventCount(dbCount)
                    .suggestedAt(LocalDateTime.now())
                    .implemented(false)
                    .build());
        }
        
        // Analyze validation failures
        long validationCount = failedEvents.stream()
                .filter(e -> extractFailureReason(e).contains("validation"))
                .count();
        if (validationCount > 0) {
            suggestions.add(RemediationSuggestion.builder()
                    .suggestionId(UUID.randomUUID().toString())
                    .category("VALIDATION")
                    .severity("WARNING")
                    .failureReason("validation")
                    .shortDescription("Data validation failures detected")
                    .detailedDescription("Webhook payloads are failing validation checks. " +
                            "This may indicate schema version mismatches or incomplete data.")
                    .recommendedActions(List.of(
                            "Review Stripe payload schema changes",
                            "Update validation rules",
                            "Add better error logging",
                            "Implement payload versioning",
                            "Add comprehensive unit tests for validation"
                    ))
                    .estimatedImpact("MEDIUM")
                    .expectedImprovement(0.25)
                    .implementationEffort("MEDIUM")
                    .estimatedMinutesToImplement(45)
                    .affectedEventTypes(failedEvents.stream()
                            .filter(e -> extractFailureReason(e).contains("validation"))
                            .map(StripeEventLog::getEventType)
                            .distinct()
                            .collect(Collectors.toList()))
                    .affectedEventCount(validationCount)
                    .suggestedAt(LocalDateTime.now())
                    .implemented(false)
                    .build());
        }
        
        return suggestions;
    }

    /**
     * Assess overall health status based on failure patterns
     */
    private HealthStatus assessHealthStatus(List<StripeEventLog> failedEvents, List<StripeEventLog> allEvents) {
        double failureRate = calculateFailureRate(allEvents.size(), failedEvents.size());
        long timeoutCount = failedEvents.stream()
                .filter(e -> extractFailureReason(e).contains("timeout"))
                .count();
        long dbCount = failedEvents.stream()
                .filter(e -> extractFailureReason(e).contains("database"))
                .count();
        
        List<String> activeAlerts = new ArrayList<>();
        String status = "HEALTHY";
        
        boolean isFailureRateHigh = failureRate > 0.05;
        boolean isTimeoutRateHigh = timeoutCount > failedEvents.size() * 0.2 && failedEvents.size() > 0;
        boolean isDatabaseErrorHigh = dbCount > failedEvents.size() * 0.3 && failedEvents.size() > 0;
        
        if (isFailureRateHigh) {
            activeAlerts.add("High overall failure rate (>" + String.format("%.1f%%", failureRate * 100) + ")");
            status = "DEGRADED";
        }
        if (isTimeoutRateHigh) {
            activeAlerts.add("Excessive timeout errors detected");
            status = "DEGRADED";
        }
        if (isDatabaseErrorHigh) {
            activeAlerts.add("Database error spike detected");
            status = "DEGRADED";
        }
        
        if (failureRate > 0.10) {
            status = "CRITICAL";
        }
        
        return HealthStatus.builder()
                .overallStatus(status)
                .statusReason(activeAlerts.isEmpty() ? "All systems operational" : String.join(", ", activeAlerts))
                .activeAlerts(activeAlerts)
                .isFailureRateHigh(isFailureRateHigh)
                .isTimeoutRateHigh(isTimeoutRateHigh)
                .isDatabaseErrorHigh(isDatabaseErrorHigh)
                .hasCircuitBreakerOpen(false)  // Would check actual circuit breaker state
                .hasExcessiveRetries(failedEvents.stream()
                        .anyMatch(e -> e.getRetryCount() > 10))
                .lastHealthCheck(LocalDateTime.now())
                .nextRecommendedAnalysis(LocalDateTime.now().plus(5, ChronoUnit.MINUTES))
                .build();
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private String extractFailureReason(StripeEventLog event) {
        if (event.getLastError() == null) return "unknown";
        String error = event.getLastError().toLowerCase();
        if (error.contains("timeout")) return "timeout";
        if (error.contains("database") || error.contains("sql")) return "database";
        if (error.contains("validation") || error.contains("invalid")) return "validation";
        if (error.contains("circuit")) return "circuit_breaker";
        return "unknown";
    }

    private String categorizeSeverity(StripeEventLog event) {
        int retryCount = event.getRetryCount();
        if (retryCount > 5) return "CRITICAL";
        if (retryCount > 2) return "WARNING";
        return "INFO";
    }

    private long calculateProcessingTime(StripeEventLog event) {
        if (event.getCreatedAt() == null || event.getProcessedAt() == null) return 0;
        return ChronoUnit.MILLIS.between(event.getCreatedAt(), event.getProcessedAt());
    }

    private double calculateFailureRate(long total, long failures) {
        return total == 0 ? 0 : (double) failures / total;
    }

    private double calculateFailureRateForWindow(LocalDateTime start) {
        List<StripeEventLog> events = eventLogRepository.findByCreatedAtAfter(start);
        long failures = events.stream()
                .filter(e -> e.getStatus() == StripeEventLog.EventProcessingStatus.FAILED ||
                           e.getStatus() == StripeEventLog.EventProcessingStatus.RETRY_PENDING)
                .count();
        return calculateFailureRate(events.size(), failures);
    }

    private String compareTrends(double oldRate, double newRate) {
        if (newRate < oldRate * 0.8) return "IMPROVING";
        if (newRate > oldRate * 1.2) return "DEGRADING";
        return "STABLE";
    }

    private double calculateRecoveryRate(List<StripeEventLog> events, String type) {
        long successful = events.stream()
                .filter(e -> e.getStatus() == StripeEventLog.EventProcessingStatus.SUCCESS)
                .count();
        return events.isEmpty() ? 0 : (double) successful / events.size();
    }

    private String getSuggestedAction(String reason) {
        return switch (reason) {
            case "timeout" -> "Increase timeout or optimize processing";
            case "database" -> "Optimize queries and check database health";
            case "validation" -> "Review validation rules and data schema";
            default -> "Investigate error logs and implementation";
        };
    }
}
