package com.leadflow.backend.service.billing.analysis;

import com.leadflow.backend.dto.analysis.FailureAnalysisDTO;
import com.leadflow.backend.dto.analysis.FailureAnalysisDTO.*;
import com.leadflow.backend.entities.StripeEventLog;
import com.leadflow.backend.repository.StripeEventLogRepository;
import com.leadflow.backend.service.billing.WebhookMetricsTracker;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Test Suite for FailureAnalysisService
 * 
 * Tests comprehensive failure analysis including:
 * - Failure pattern detection and categorization
 * - Trend analysis across time windows
 * - Automated remediation suggestions
 * - Health status assessment
 * - Edge cases (empty data, single failures, etc.)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("FailureAnalysisService Test Suite")
class FailureAnalysisServiceTest {

    private FailureAnalysisService failureAnalysisService;

    @Mock
    private StripeEventLogRepository eventLogRepository;

    private WebhookMetricsTracker metricsTracker;
    private SimpleMeterRegistry meterRegistry;
    private UUID testTenantId;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsTracker = new WebhookMetricsTracker(meterRegistry);
        metricsTracker.initialize();
        
        failureAnalysisService = new FailureAnalysisService(
                eventLogRepository,
                metricsTracker,
                meterRegistry
        );
        
        testTenantId = UUID.randomUUID();
    }

    // =====================================================
    // TEST SCENARIO 1-3: Basic Failure Analysis
    // =====================================================

    @Test
    @DisplayName("Scenario 1: analyzeFailures - Success with mixed events")
    void testAnalyzeFailures_MixedEvents() {
        // Arrange
        List<StripeEventLog> events = createMixedEventList(10, 3, 2);
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        assertThat(analysis).isNotNull();
        assertThat(analysis.getTotalEventsAnalyzed()).isEqualTo(10);
        assertThat(analysis.getTotalFailures()).isGreaterThan(0);
        assertThat(analysis.getOverallFailureRate()).isGreaterThan(0.0);
        assertThat(analysis.getAnalysisId()).isNotNull();
        assertThat(analysis.getAnalyzedAt()).isNotNull();
    }

    @Test
    @DisplayName("Scenario 2: analyzeFailures - No failures detected")
    void testAnalyzeFailures_NoFailures() {
        // Arrange - all events are successful
        List<StripeEventLog> events = createSuccessfulEventList(5);
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        assertThat(analysis.getTotalFailures()).isEqualTo(0);
        assertThat(analysis.getOverallFailureRate()).isEqualTo(0.0);
        assertThat(analysis.getSuggestions()).isEmpty();
    }

    @Test
    @DisplayName("Scenario 3: analyzeFailures - Empty data")
    void testAnalyzeFailures_EmptyEvents() {
        // Arrange
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(new ArrayList<>());
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        assertThat(analysis.getTotalEventsAnalyzed()).isEqualTo(0);
        assertThat(analysis.getTotalFailures()).isEqualTo(0);
        assertThat(analysis.getFailureBreakdown()).isNotNull();
    }

    // =====================================================
    // TEST SCENARIO 4-6: Failure Categorization
    // =====================================================

    @Test
    @DisplayName("Scenario 4: analyzeFailures - Timeout failures categorized correctly")
    void testAnalyzeFailures_TimeoutCategorization() {
        // Arrange - create timeout failures
        List<StripeEventLog> events = new ArrayList<>();
        for (int i = 0; i < 3; i++) {
            events.add(createFailedEvent("timeout", "customer.created"));
        }
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        FailureBreakdown breakdown = analysis.getFailureBreakdown();
        assertThat(breakdown.getTotalTimeouts()).isEqualTo(3);
        assertThat(breakdown.getByReason()).containsKey("timeout");
    }

    @Test
    @DisplayName("Scenario 5: analyzeFailures - Database failures detected")
    void testAnalyzeFailures_DatabaseCategorization() {
        // Arrange
        List<StripeEventLog> events = new ArrayList<>();
        for (int i = 0; i < 2; i++) {
            events.add(createFailedEvent("database", "invoice.created"));
        }
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        assertThat(analysis.getFailureBreakdown().getTotalDatabaseErrors()).isEqualTo(2);
    }

    @Test
    @DisplayName("Scenario 6: analyzeFailures - Multiple failure types detected")
    void testAnalyzeFailures_MultipleReasons() {
        // Arrange - mix of different failure reasons
        List<StripeEventLog> events = new ArrayList<>();
        events.addAll(createFailedEventList("timeout", 3));
        events.addAll(createFailedEventList("database", 2));
        events.addAll(createFailedEventList("validation", 1));
        
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        FailureBreakdown breakdown = analysis.getFailureBreakdown();
        assertThat(breakdown.getTotalTimeouts()).isEqualTo(3);
        assertThat(breakdown.getTotalDatabaseErrors()).isEqualTo(2);
        assertThat(breakdown.getTotalValidationErrors()).isEqualTo(1);
    }

    // =====================================================
    // TEST SCENARIO 7-9: Health Status Assessment
    // =====================================================

    @Test
    @DisplayName("Scenario 7: assessHealthStatus - HEALTHY when no failures")
    void testHealthStatus_Healthy() {
        // Arrange
        List<StripeEventLog> events = createSuccessfulEventList(10);
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        assertThat(analysis.getHealthStatus().getOverallStatus()).isEqualTo("HEALTHY");
        assertThat(analysis.getHealthStatus().getActiveAlerts()).isEmpty();
    }

    @Test
    @DisplayName("Scenario 8: assessHealthStatus - DEGRADED with high failure rate")
    void testHealthStatus_Degraded() {
        // Arrange - 10% failure rate
        List<StripeEventLog> events = createMixedEventList(10, 1, 0);
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        HealthStatus health = analysis.getHealthStatus();
        assertThat(health.getOverallStatus()).isIn("DEGRADED", "CRITICAL");
        assertThat(health.getActiveAlerts()).isNotEmpty();
    }

    @Test
    @DisplayName("Scenario 9: assessHealthStatus - CRITICAL with excessive failures")
    void testHealthStatus_Critical() {
        // Arrange - 15% failure rate
        List<StripeEventLog> events = createMixedEventList(20, 3, 0);
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        assertThat(analysis.getHealthStatus().getOverallStatus()).isEqualTo("CRITICAL");
    }

    // =====================================================
    // TEST SCENARIO 10-12: Remediation Suggestions
    // =====================================================

    @Test
    @DisplayName("Scenario 10: generateRemediationSuggestions - Timeout suggestion created")
    void testSuggestions_TimeoutDetected() {
        // Arrange
        List<StripeEventLog> events = createFailedEventList("timeout", 5);
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        List<RemediationSuggestion> suggestions = analysis.getSuggestions();
        assertThat(suggestions).isNotEmpty();
        assertThat(suggestions.stream()
                .map(RemediationSuggestion::getCategory)
                .toList())
                .contains("TIMEOUT");
    }

    @Test
    @DisplayName("Scenario 11: generateRemediationSuggestions - Database issue suggestion")
    void testSuggestions_DatabaseDetected() {
        // Arrange
        List<StripeEventLog> events = createFailedEventList("database", 4);
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        List<RemediationSuggestion> suggestions = analysis.getSuggestions();
        assertThat(suggestions.stream()
                .map(RemediationSuggestion::getCategory)
                .toList())
                .contains("DATABASE");
    }

    @Test
    @DisplayName("Scenario 12: generateRemediationSuggestions - Multiple suggestions")
    void testSuggestions_Multiple() {
        // Arrange - multiple failure types
        List<StripeEventLog> events = new ArrayList<>();
        events.addAll(createFailedEventList("timeout", 3));
        events.addAll(createFailedEventList("database", 2));
        events.addAll(createFailedEventList("validation", 1));
        
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        assertThat(analysis.getSuggestions().size()).isGreaterThan(1);
    }

    // =====================================================
    // TEST SCENARIO 13-15: Trend Analysis
    // =====================================================

    @Test
    @DisplayName("Scenario 13: trendAnalysis - Failure rate calculated correctly")
    void testTrendAnalysis_FailureRate() {
        // Arrange
        List<StripeEventLog> events = createMixedEventList(10, 2, 0); // 20% fail rate
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        TrendAnalysis trends = analysis.getTrendAnalysis();
        assertThat(trends.getFailureRate_24h()).isGreaterThan(0.15);
        assertThat(trends.getFailureRate_24h()).isLessThan(0.25);
    }

    @Test
    @DisplayName("Scenario 14: trendAnalysis - Trend direction detected")
    void testTrendAnalysis_TrendDirection() {
        // Arrange
        List<StripeEventLog> events = createMixedEventList(15, 1, 0);
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        TrendAnalysis trends = analysis.getTrendAnalysis();
        assertThat(trends.getTrend_24h_vs_1h())
                .isIn("IMPROVING", "STABLE", "DEGRADING");
    }

    @Test
    @DisplayName("Scenario 15: trendAnalysis - Recovery rates calculated")
    void testTrendAnalysis_RecoveryRates() {
        // Arrange
        List<StripeEventLog> events = new ArrayList<>();
        events.addAll(createSuccessfulEventList(7));
        events.addAll(createFailedEventList("timeout", 3));
        
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        TrendAnalysis trends = analysis.getTrendAnalysis();
        assertThat(trends.getRecoveryRate_success()).isGreaterThan(0.0);
    }

    // =====================================================
    // TEST SCENARIO 16-18: Edge Cases & Special Scenarios
    // =====================================================

    @Test
    @DisplayName("Scenario 16: analyzeFailures - Excessive retries detected")
    void testAnalyzeFailures_ExcessiveRetries() {
        // Arrange - event with many retries
        List<StripeEventLog> events = new ArrayList<>();
        StripeEventLog event = createFailedEvent("timeout", "customer.created");
        event.setRetryCount(12);  // > 10 is excessive
        events.add(event);
        
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        assertThat(analysis.getHealthStatus().isHasExcessiveRetries()).isTrue();
    }

    @Test
    @DisplayName("Scenario 17: analyzeFailures - Recent failures populated")
    void testAnalyzeFailures_RecentFailures() {
        // Arrange
        List<StripeEventLog> events = createFailedEventList("timeout", 15);
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert
        List<RecentFailure> recent = analysis.getFailureBreakdown().getRecentFailures();
        assertThat(recent).isNotEmpty();
        assertThat(recent.size()).isLessThanOrEqualTo(10);  // Max 10 recent
    }

    @Test
    @DisplayName("Scenario 18: analyzeFailures - Complete lifecycle analysis")
    void testAnalyzeFailures_CompleteWorkflow() {
        // Arrange - simulate complete workflow
        List<StripeEventLog> events = new ArrayList<>();
        // 70% successful
        events.addAll(createSuccessfulEventList(7));
        // 20% timeout
        events.addAll(createFailedEventList("timeout", 2));
        // 10% database
        events.addAll(createFailedEventList("database", 1));
        
        when(eventLogRepository.findByCreatedAtAfter(any(LocalDateTime.class)))
                .thenReturn(events);
        
        // Act
        FailureAnalysisDTO analysis = failureAnalysisService.analyzeFailures(86400);
        
        // Assert - verify all components populated
        assertThat(analysis.getAnalysisId()).isNotNull();
        assertThat(analysis.getFailureBreakdown()).isNotNull();
        assertThat(analysis.getTrendAnalysis()).isNotNull();
        assertThat(analysis.getSuggestions()).isNotNull();
        assertThat(analysis.getHealthStatus()).isNotNull();
        
        // Verify metrics
        assertThat(analysis.getTotalEventsAnalyzed()).isEqualTo(10);
        assertThat(analysis.getTotalFailures()).isEqualTo(3);
        assertThat(analysis.getOverallFailureRate()).isEqualTo(0.3);
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private List<StripeEventLog> createSuccessfulEventList(int count) {
        List<StripeEventLog> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            StripeEventLog event = StripeEventLog.builder()
                    .id((long) (i + 1))
                    .eventId("evt_" + i)
                    .eventType("customer.created")
                    .customerId("cus_" + i)
                    .tenantId(testTenantId)
                    .status(StripeEventLog.EventProcessingStatus.SUCCESS)
                    .retryCount(0)
                    .createdAt(LocalDateTime.now().minusHours(1))
                    .processedAt(LocalDateTime.now())
                    .lastError(null)
                    .build();
            events.add(event);
        }
        return events;
    }

    private List<StripeEventLog> createMixedEventList(int total, int failed, int pending) {
        List<StripeEventLog> events = new ArrayList<>();
        
        for (int i = 0; i < total - failed - pending; i++) {
            events.add(createSuccessfulEvent(i));
        }
        for (int i = total - failed - pending; i < total - pending; i++) {
            events.add(createFailedEvent("timeout", "customer.created"));
        }
        for (int i = total - pending; i < total; i++) {
            events.add(createPendingEvent(i));
        }
        return events;
    }

    private StripeEventLog createSuccessfulEvent(int index) {
        return StripeEventLog.builder()
                .id((long) (index + 1))
                .eventId("evt_" + index)
                .eventType("customer.created")
                .customerId("cus_" + index)
                .tenantId(testTenantId)
                .status(StripeEventLog.EventProcessingStatus.SUCCESS)
                .retryCount(0)
                .createdAt(LocalDateTime.now().minusHours(1))
                .processedAt(LocalDateTime.now())
                .lastError(null)
                .build();
    }

    private StripeEventLog createFailedEvent(String reason, String eventType) {
        return StripeEventLog.builder()
                .id(System.nanoTime())
                .eventId("evt_" + UUID.randomUUID())
                .eventType(eventType)
                .customerId("cus_" + UUID.randomUUID())
                .tenantId(testTenantId)
                .status(StripeEventLog.EventProcessingStatus.FAILED)
                .retryCount(1)
                .createdAt(LocalDateTime.now().minusHours(1))
                .processedAt(LocalDateTime.now())
                .lastError("Error: " + reason)
                .build();
    }

    private List<StripeEventLog> createFailedEventList(String reason, int count) {
        List<StripeEventLog> events = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            events.add(createFailedEvent(reason, "customer.created"));
        }
        return events;
    }

    private StripeEventLog createPendingEvent(int index) {
        return StripeEventLog.builder()
                .id((long) (index + 1))
                .eventId("evt_" + index)
                .eventType("charge.succeeded")
                .customerId("cus_" + index)
                .tenantId(testTenantId)
                .status(StripeEventLog.EventProcessingStatus.RETRY_PENDING)
                .retryCount(1)
                .createdAt(LocalDateTime.now().minusHours(2))
                .lastError("Temporary failure")
                .nextRetryAt(LocalDateTime.now().plusMinutes(5))
                .build();
    }
}
