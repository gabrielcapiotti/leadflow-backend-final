package com.leadflow.backend.controller.billing;

import com.leadflow.backend.dto.analysis.FailureAnalysisDTO;
import com.leadflow.backend.service.billing.analysis.FailureAnalysisService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for Webhook Failure Analysis
 * 
 * Provides endpoints for:
 * - Real-time failure analysis across different time windows
 * - Failure trend analysis (30d, 7d, 24h trends)
 * - Automated remediation suggestions
 * - Health status assessment and alerts
 * 
 * All endpoints require ADMIN role
 */
@RestController
@RequestMapping("/api/v1/billing/webhooks/analysis")
@RequiredArgsConstructor
@Slf4j
@PreAuthorize("hasRole('ADMIN')")
public class FailureAnalysisController {

    private final FailureAnalysisService failureAnalysisService;

    /**
     * GET /api/v1/billing/webhooks/analysis/failures
     * 
     * Get comprehensive failure analysis for last 24 hours
     * 
     * @return Failure analysis with breakdown, trends, suggestions, health status
     */
    @GetMapping("/failures")
    public ResponseEntity<FailureAnalysisDTO> getFailureAnalysis24h() {
        log.info("[ANALYSIS] Request: Failure analysis for 24 hours");
        try {
            FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400); // 24 hours
            log.info("[ANALYSIS] Success: Generated analysis ID {}", analysis.getAnalysisId());
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("[ANALYSIS] Error analyzing failures", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * GET /api/v1/billing/webhooks/analysis/failures/7d
     * 
     * Get comprehensive failure analysis for last 7 days
     * 
     * @return Failure analysis
     */
    @GetMapping("/failures/7d")
    public ResponseEntity<FailureAnalysisDTO> getFailureAnalysis7d() {
        log.info("[ANALYSIS] Request: Failure analysis for 7 days");
        try {
            FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(604800); // 7 days
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("[ANALYSIS] Error analyzing 7-day failures", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * GET /api/v1/billing/webhooks/analysis/failures/30d
     * 
     * Get comprehensive failure analysis for last 30 days
     * 
     * @return Failure analysis
     */
    @GetMapping("/failures/30d")
    public ResponseEntity<FailureAnalysisDTO> getFailureAnalysis30d() {
        log.info("[ANALYSIS] Request: Failure analysis for 30 days");
        try {
            FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(2592000); // 30 days
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("[ANALYSIS] Error analyzing 30-day failures", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * GET /api/v1/billing/webhooks/analysis/failures/window
     * 
     * Get failure analysis for custom time window (in seconds)
     * 
     * @param windowSeconds - Window size in seconds
     * @return Failure analysis
     */
    @GetMapping("/failures/window")
    public ResponseEntity<FailureAnalysisDTO> getFailureAnalysisWindow(
            @RequestParam(defaultValue = "86400") long windowSeconds) {
        
        // Validate window size (min 1 hour, max 365 days)
        if (windowSeconds < 3600 || windowSeconds > 31536000) {
            log.warn("[ANALYSIS] Invalid window size: {} seconds", windowSeconds);
            return ResponseEntity.badRequest().build();
        }
        
        log.info("[ANALYSIS] Request: Failure analysis for {} seconds", windowSeconds);
        try {
            FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(windowSeconds);
            return ResponseEntity.ok(analysis);
        } catch (Exception e) {
            log.error("[ANALYSIS] Error analyzing failures for window {}", windowSeconds, e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * GET /api/v1/billing/webhooks/analysis/trends
     * 
     * Get trend analysis across multiple time windows
     * 
     * @return Map with failure rates and trends for different periods
     */
    @GetMapping("/trends")
    public ResponseEntity<Map<String, Object>> getTrendAnalysis() {
        log.info("[ANALYSIS] Request: Trend analysis across all windows");
        try {
            FailureAnalysisDTO analysis24h = failureAnalysisService.analyzeFailures(86400);
            
            Map<String, Object> trends = new HashMap<>();
            trends.put("failure_rate_24h", analysis24h.getOverallFailureRate());
            trends.put("trend_analysis", analysis24h.getTrendAnalysis());
            trends.put("total_events", analysis24h.getTotalEventsAnalyzed());
            trends.put("total_failures", analysis24h.getTotalFailures());
            trends.put("analyzed_at", analysis24h.getAnalyzedAt());
            
            log.info("[ANALYSIS] Trends retrieved successfully");
            return ResponseEntity.ok(trends);
        } catch (Exception e) {
            log.error("[ANALYSIS] Error retrieving trend analysis", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * GET /api/v1/billing/webhooks/analysis/recommendations
     * 
     * Get automated remediation recommendations based on failure analysis
     * 
     * @return List of remediation suggestions with severity, impact, effort
     */
    @GetMapping("/recommendations")
    public ResponseEntity<Map<String, Object>> getRecommendations() {
        log.info("[ANALYSIS] Request: Remediation recommendations");
        try {
            FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
            
            Map<String, Object> recommendations = new HashMap<>();
            recommendations.put("analysis_id", analysis.getAnalysisId());
            recommendations.put("suggestions", analysis.getSuggestions());
            recommendations.put("suggestion_count", analysis.getSuggestions().size());
            recommendations.put("critical_suggestions", analysis.getSuggestions().stream()
                    .filter(s -> "CRITICAL".equals(s.getSeverity()))
                    .count());
            recommendations.put("health_status", analysis.getHealthStatus());
            recommendations.put("recommended_actions", extractTopActions(analysis));
            recommendations.put("analyzed_at", analysis.getAnalyzedAt());
            
            log.info("[ANALYSIS] Generated {} recommendations", analysis.getSuggestions().size());
            return ResponseEntity.ok(recommendations);
        } catch (Exception e) {
            log.error("[ANALYSIS] Error generating recommendations", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * GET /api/v1/billing/webhooks/analysis/health
     * 
     * Get current health status with active alerts
     * 
     * @return Health status with active alerts and recommendations
     */
    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> getHealthStatus() {
        log.info("[ANALYSIS] Request: Health status");
        try {
            FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
            FailureAnalysisDTO.HealthStatus health = analysis.getHealthStatus();
            
            Map<String, Object> response = new HashMap<>();
            response.put("status", health.getOverallStatus());
            response.put("status_reason", health.getStatusReason());
            response.put("active_alerts", health.getActiveAlerts());
            response.put("metrics", Map.of(
                    "failure_rate_high", health.isFailureRateHigh(),
                    "timeout_rate_high", health.isTimeoutRateHigh(),
                    "database_error_high", health.isDatabaseErrorHigh(),
                    "circuit_breaker_open", health.isHasCircuitBreakerOpen(),
                    "excessive_retries", health.isHasExcessiveRetries()
            ));
            response.put("last_health_check", health.getLastHealthCheck());
            response.put("next_recommended_analysis", health.getNextRecommendedAnalysis());
            
            log.info("[ANALYSIS] Health check completed: {}", health.getOverallStatus());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[ANALYSIS] Error getting health status", e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * GET /api/v1/billing/webhooks/analysis/breakdown
     * 
     * Get detailed breakdown of failures by reason, event type, severity
     * 
     * @return Failure breakdown with categorization
     */
    @GetMapping("/breakdown")
    public ResponseEntity<Map<String, Object>> getFailureBreakdown() {
        log.info("[ANALYSIS] Request: Failure breakdown");
        try {
            FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
            FailureAnalysisDTO.FailureBreakdown breakdown = analysis.getFailureBreakdown();
            
            Map<String, Object> response = new HashMap<>();
            response.put("by_reason", breakdown.getByReason());
            response.put("by_event_type", breakdown.getByEventType());
            response.put("by_severity", breakdown.getBySeverity());
            response.put("error_summary", Map.of(
                    "timeouts", breakdown.getTotalTimeouts(),
                    "database_errors", breakdown.getTotalDatabaseErrors(),
                    "validation_errors", breakdown.getTotalValidationErrors(),
                    "unknown_errors", breakdown.getTotalUnknownErrors()
            ));
            response.put("recent_failures", breakdown.getRecentFailures());
            
            log.info("[ANALYSIS] Breakdown retrieved: {} categories", breakdown.getByReason().size());
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("[ANALYSIS] Error getting failure breakdown", e);
            return ResponseEntity.status(500).build();
        }
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private Map<String, Object> extractTopActions(FailureAnalysisDTO analysis) {
        Map<String, Object> topActions = new HashMap<>();
        
        analysis.getSuggestions().stream()
                .filter(s -> "CRITICAL".equals(s.getSeverity()))
                .limit(3)
                .forEach(s -> topActions.put(s.getCategory(), s.getRecommendedActions()));
        
        return topActions;
    }
}
