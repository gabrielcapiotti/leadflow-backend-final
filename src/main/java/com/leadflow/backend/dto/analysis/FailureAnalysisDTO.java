package com.leadflow.backend.dto.analysis;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * Data Transfer Object for Failure Analysis
 * 
 * Provides comprehensive failure pattern analysis including:
 * - Failure categorization by reason, event type, severity
 * - Trend analysis (30d, 7d, 24h, 1h windows)
 * - Automated remediation suggestions
 * - Recovery recommendations and severity levels
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FailureAnalysisDTO {

    // =====================================================
    // CORE METRICS
    // =====================================================
    
    private String analysisId;
    private LocalDateTime analyzedAt;
    private long analysisWindowSeconds;  // e.g., 86400 for 24h
    private long totalEventsAnalyzed;
    private long totalFailures;
    private double overallFailureRate;  // 0.0 - 1.0
    
    // =====================================================
    // FAILURE BREAKDOWN
    // =====================================================
    
    private FailureBreakdown failureBreakdown;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FailureBreakdown {
        
        // Falhas agrupadas por razão (timeout, database, validation, etc)
        private Map<String, FailureReasonMetrics> byReason;
        
        // Falhas agrupadas por tipo de evento (customer.created, charge.failed, etc)
        private Map<String, FailureEventMetrics> byEventType;
        
        // Falhas agrupadas por severidade (CRITICAL, WARNING, INFO)
        private Map<String, Long> bySeverity;
        
        // Falhas mais recentes
        private List<RecentFailure> recentFailures;
        
        private long totalTimeouts;
        private long totalDatabaseErrors;
        private long totalValidationErrors;
        private long totalUnknownErrors;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FailureReasonMetrics {
        private String reason;
        private long count;
        private double percentage;
        private long avgProcessingTimeMs;
        private long maxProcessingTimeMs;
        private int recoveryAttempts;
        private double recoverySuccessRate;
        private String suggestedAction;
        private String severityLevel;  // CRITICAL, WARNING, INFO
        private LocalDateTime firstOccurrence;
        private LocalDateTime lastOccurrence;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class FailureEventMetrics {
        private String eventType;
        private long totalReceived;
        private long totalFailed;
        private double failureRate;
        private long avgRetryCount;
        private double retrySuccessRate;
        private String topFailureReason;
        private List<String> recentFailureReasons;
        private String recommendedAction;
    }
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RecentFailure {
        private String eventId;
        private String eventType;
        private String reason;
        private long processingTimeMs;
        private int retryCount;
        private LocalDateTime occurredAt;
        private String status;  // FAILED, RECOVERED, PENDING
    }
    
    // =====================================================
    // TREND ANALYSIS
    // =====================================================
    
    private TrendAnalysis trendAnalysis;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class TrendAnalysis {
        
        // Taxas de falha em diferentes períodos
        private double failureRate_30d;    // Last 30 days
        private double failureRate_7d;     // Last 7 days
        private double failureRate_24h;    // Last 24 hours
        private double failureRate_1h;     // Last 1 hour
        
        // Tendências (trending up/stable/down)
        private String trend_30d_vs_7d;    // "IMPROVING", "STABLE", "DEGRADING"
        private String trend_7d_vs_24h;
        private String trend_24h_vs_1h;
        
        // Horas de pico de falha
        private Map<Integer, Long> failuresByHour;  // hour -> count
        private Map<String, Long> failuresByDayOfWeek;
        
        // Taxa de recuperação
        private double recoveryRate_success;
        private double recoveryRate_timeout;
        private double recoveryRate_retry;
        
        private LocalDateTime analysisStart;
        private LocalDateTime analysisEnd;
    }
    
    // =====================================================
    // REMEDIATION SUGGESTIONS
    // =====================================================
    
    private List<RemediationSuggestion> suggestions;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class RemediationSuggestion {
        
        private String suggestionId;
        private String category;  // "TIMEOUT", "DATABASE", "VALIDATION", "CIRCUIT_BREAKER", "RETRY_STRATEGY"
        private String severity;  // "CRITICAL", "WARNING", "INFO"
        private String failureReason;
        private String shortDescription;
        private String detailedDescription;
        
        // Recommended actions
        private List<String> recommendedActions;
        
        // Estimated impact
        private String estimatedImpact;  // "HIGH", "MEDIUM", "LOW"
        private double expectedImprovement;  // percentage
        
        // Implementation effort
        private String implementationEffort;  // "LOW", "MEDIUM", "HIGH"
        private int estimatedMinutesToImplement;
        
        // Related events
        private List<String> affectedEventTypes;
        private long affectedEventCount;
        private LocalDateTime suggestedAt;
        private boolean implemented;
    }
    
    // =====================================================
    // HEALTH STATUS
    // =====================================================
    
    private HealthStatus healthStatus;
    
    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class HealthStatus {
        
        private String overallStatus;  // "HEALTHY", "DEGRADED", "CRITICAL"
        private String statusReason;
        private List<String> activeAlerts;
        
        // Critical thresholds
        private boolean isFailureRateHigh;        // > 5%
        private boolean isTimeoutRateHigh;        // > 20% of failures
        private boolean isDatabaseErrorHigh;      // > 30% of failures
        private boolean hasCircuitBreakerOpen;
        private boolean hasExcessiveRetries;      // > 10 retries for single event
        
        private LocalDateTime lastHealthCheck;
        private LocalDateTime nextRecommendedAnalysis;
    }
}
