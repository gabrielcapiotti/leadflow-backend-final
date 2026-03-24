package com.leadflow.backend.service.billing;

import com.leadflow.backend.entities.WebhookAlertEvent;
import com.leadflow.backend.repository.WebhookAlertRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * WebhookAlertServiceTest - Comprehensive test scenarios for alert management
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("Webhook Alert Service Tests")
class WebhookAlertServiceTest {

    @Mock
    private WebhookAlertRepository alertRepository;

    @InjectMocks
    private WebhookAlertService alertService;

    private UUID testTenantId;
    private WebhookAlertEvent testAlert;

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        
        testAlert = WebhookAlertEvent.builder()
                .id(UUID.randomUUID())
                .alertType(WebhookAlertEvent.AlertType.CIRCUIT_BREAKER_OPENED)
                .severity(WebhookAlertEvent.AlertSeverity.CRITICAL)
                .tenantId(testTenantId)
                .message("Test alert")
                .metrics(Map.of("test", "value"))
                .build();
    }

    // =====================================================
    // ALERT CREATION TESTS
    // =====================================================

    @Test
    @DisplayName("Should create a new alert when no duplicate exists")
    void testCreateAlert_Success() {
        // Arrange
        when(alertRepository.findRecentUnresolvedSameType(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(alertRepository.save(any(WebhookAlertEvent.class)))
                .thenReturn(testAlert);

        // Act
        WebhookAlertEvent result = alertService.createAlert(
                WebhookAlertEvent.AlertType.CIRCUIT_BREAKER_OPENED,
                WebhookAlertEvent.AlertSeverity.CRITICAL,
                testTenantId,
                "Circuit breaker opened",
                Map.of("failures", 10)
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getAlertType()).isEqualTo(WebhookAlertEvent.AlertType.CIRCUIT_BREAKER_OPENED);
        verify(alertRepository).save(any(WebhookAlertEvent.class));
    }

    @Test
    @DisplayName("Should deduplicate alerts within threshold period")
    void testCreateAlert_Deduplicated() {
        // Arrange
        List<WebhookAlertEvent> existingAlerts = Collections.singletonList(testAlert);
        when(alertRepository.findRecentUnresolvedSameType(any(), any(), any()))
                .thenReturn(existingAlerts);

        // Act
        WebhookAlertEvent result = alertService.createAlert(
                WebhookAlertEvent.AlertType.CIRCUIT_BREAKER_OPENED,
                WebhookAlertEvent.AlertSeverity.CRITICAL,
                testTenantId,
                "Circuit breaker opened",
                Map.of("failures", 10)
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getId()).isEqualTo(testAlert.getId());
        verify(alertRepository, never()).save(any());  // Should not save new alert
    }

    @Test
    @DisplayName("Should include metrics when creating alert")
    void testCreateAlert_WithMetrics() {
        // Arrange
        Map<String, Object> metrics = Map.of(
                "failureCount", 10,
                "threshold", 10,
                "timestamp", System.currentTimeMillis()
        );
        when(alertRepository.findRecentUnresolvedSameType(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(alertRepository.save(any(WebhookAlertEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        alertService.createAlert(
                WebhookAlertEvent.AlertType.CIRCUIT_BREAKER_OPENED,
                WebhookAlertEvent.AlertSeverity.CRITICAL,
                testTenantId,
                "Test",
                metrics
        );

        // Assert
        ArgumentCaptor<WebhookAlertEvent> captor = ArgumentCaptor.forClass(WebhookAlertEvent.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getMetrics()).containsKeys("failureCount", "threshold", "timestamp");
    }

    // =====================================================
    // ALERT RESOLUTION TESTS
    // =====================================================

    @Test
    @DisplayName("Should resolve an unresolved alert")
    void testResolveAlert_Success() {
        // Arrange
        UUID alertId = UUID.randomUUID();
        WebhookAlertEvent alert = WebhookAlertEvent.builder()
                .id(alertId)
                .alertType(WebhookAlertEvent.AlertType.CIRCUIT_BREAKER_OPENED)
                .severity(WebhookAlertEvent.AlertSeverity.CRITICAL)
                .tenantId(testTenantId)
                .resolvedAt(null)
                .build();

        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));
        when(alertRepository.save(any(WebhookAlertEvent.class))).thenReturn(alert);

        // Act
        WebhookAlertEvent result = alertService.resolveAlert(alertId);

        // Assert
        assertThat(result).isNotNull();
        verify(alertRepository).save(any(WebhookAlertEvent.class));
    }

    @Test
    @DisplayName("Should return null when alert not found")
    void testResolveAlert_NotFound() {
        // Arrange
        UUID alertId = UUID.randomUUID();
        when(alertRepository.findById(alertId)).thenReturn(Optional.empty());

        // Act
        WebhookAlertEvent result = alertService.resolveAlert(alertId);

        // Assert
        assertThat(result).isNull();
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle already resolved alerts")
    void testResolveAlert_AlreadyResolved() {
        // Arrange
        UUID alertId = UUID.randomUUID();
        WebhookAlertEvent alert = WebhookAlertEvent.builder()
                .id(alertId)
                .resolvedAt(LocalDateTime.now().minusMinutes(5))
                .tenantId(testTenantId)
                .build();

        when(alertRepository.findById(alertId)).thenReturn(Optional.of(alert));

        // Act
        alertService.resolveAlert(alertId);

        // Assert
        verify(alertRepository, never()).save(any());
    }

    // =====================================================
    // ALERT TRIGGER TESTS
    // =====================================================

    @Test
    @DisplayName("Should trigger CIRCUIT_BREAKER_OPENED alert when CB is open")
    void testCheckCircuitBreakerStatus_AlertTriggered() {
        // Arrange
        CircuitBreakerConfig circuitBreaker = mock(CircuitBreakerConfig.class);
        when(circuitBreaker.getState()).thenReturn(CircuitBreakerConfig.CircuitState.OPEN);
        when(circuitBreaker.getFailureCount()).thenReturn(10);

        when(alertRepository.findRecentUnresolvedSameType(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(alertRepository.save(any(WebhookAlertEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        alertService.checkCircuitBreakerStatus(circuitBreaker, testTenantId);

        // Assert
        ArgumentCaptor<WebhookAlertEvent> captor = ArgumentCaptor.forClass(WebhookAlertEvent.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getAlertType())
                .isEqualTo(WebhookAlertEvent.AlertType.CIRCUIT_BREAKER_OPENED);
        assertThat(captor.getValue().getSeverity())
                .isEqualTo(WebhookAlertEvent.AlertSeverity.CRITICAL);
    }

    @Test
    @DisplayName("Should trigger HIGH_FAILURE_RATE alert when rate exceeds 50%")
    void testCheckFailureRate_AlertTriggered() {
        // Arrange
        when(alertRepository.findRecentUnresolvedSameType(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(alertRepository.save(any(WebhookAlertEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        alertService.checkFailureRate(testTenantId, 75, 100);  // 75% failure rate

        // Assert
        ArgumentCaptor<WebhookAlertEvent> captor = ArgumentCaptor.forClass(WebhookAlertEvent.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getAlertType())
                .isEqualTo(WebhookAlertEvent.AlertType.HIGH_FAILURE_RATE);
        assertThat(captor.getValue().getSeverity())
                .isEqualTo(WebhookAlertEvent.AlertSeverity.CRITICAL);
    }

    @Test
    @DisplayName("Should NOT trigger alert when failure rate is below 50%")
    void testCheckFailureRate_NoAlert() {
        // Act
        alertService.checkFailureRate(testTenantId, 25, 100);  // 25% failure rate

        // Assert
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should trigger PROCESSING_STALLED alert when no processing for 10+ minutes")
    void testCheckProcessingStalled_AlertTriggered() {
        // Arrange
        LocalDateTime elevenMinutesAgo = LocalDateTime.now().minusMinutes(11);
        when(alertRepository.findRecentUnresolvedSameType(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(alertRepository.save(any(WebhookAlertEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        alertService.checkProcessingStalled(testTenantId, elevenMinutesAgo);

        // Assert
        ArgumentCaptor<WebhookAlertEvent> captor = ArgumentCaptor.forClass(WebhookAlertEvent.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getAlertType())
                .isEqualTo(WebhookAlertEvent.AlertType.PROCESSING_STALLED);
    }

    @Test
    @DisplayName("Should trigger EXCESSIVE_RETRIES alert when retry count >= 5")
    void testCheckExcessiveRetries_AlertTriggered() {
        // Arrange
        when(alertRepository.findRecentUnresolvedSameType(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(alertRepository.save(any(WebhookAlertEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        alertService.checkExcessiveRetries(testTenantId, "evt_123", 7, 10);

        // Assert
        ArgumentCaptor<WebhookAlertEvent> captor = ArgumentCaptor.forClass(WebhookAlertEvent.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getAlertType())
                .isEqualTo(WebhookAlertEvent.AlertType.EXCESSIVE_RETRIES);
    }

    @Test
    @DisplayName("Should trigger TIMEOUT_DETECTED alert when latency exceeds 5 seconds")
    void testCheckLatency_AlertTriggered() {
        // Arrange
        when(alertRepository.findRecentUnresolvedSameType(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(alertRepository.save(any(WebhookAlertEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        alertService.checkLatency(testTenantId, 6500.0);  // 6.5 seconds

        // Assert
        ArgumentCaptor<WebhookAlertEvent> captor = ArgumentCaptor.forClass(WebhookAlertEvent.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getAlertType())
                .isEqualTo(WebhookAlertEvent.AlertType.TIMEOUT_DETECTED);
    }

    // =====================================================
    // ALERT RETRIEVAL TESTS
    // =====================================================

    @Test
    @DisplayName("Should retrieve all active alerts")
    void testGetActiveAlerts_Success() {
        // Arrange
        List<WebhookAlertEvent> activeAlerts = Arrays.asList(testAlert, testAlert);
        when(alertRepository.findActiveAlerts()).thenReturn(activeAlerts);

        // Act
        List<WebhookAlertEvent> result = alertService.getActiveAlerts();

        // Assert
        assertThat(result).hasSize(2);
        verify(alertRepository).findActiveAlerts();
    }

    @Test
    @DisplayName("Should retrieve active alerts for specific tenant")
    void testGetActiveAlertsByTenant_Success() {
        // Arrange
        List<WebhookAlertEvent> tenantAlerts = Collections.singletonList(testAlert);
        when(alertRepository.findActivAlertsByTenant(testTenantId)).thenReturn(tenantAlerts);

        // Act
        List<WebhookAlertEvent> result = alertService.getActiveAlertsByTenant(testTenantId);

        // Assert
        assertThat(result).hasSize(1);
        verify(alertRepository).findActivAlertsByTenant(testTenantId);
    }

    @Test
    @DisplayName("Should retrieve critical alerts")
    void testGetCriticalAlerts_Success() {
        // Arrange
        List<WebhookAlertEvent> criticalAlerts = Collections.singletonList(testAlert);
        when(alertRepository.findCriticalUnresolved()).thenReturn(criticalAlerts);

        // Act
        List<WebhookAlertEvent> result = alertService.getCriticalAlerts();

        // Assert
        assertThat(result).hasSize(1);
        verify(alertRepository).findCriticalUnresolved();
    }

    @Test
    @DisplayName("Should retrieve alert history for tenant")
    void testGetAlertHistory_Success() {
        // Arrange
        LocalDateTime since = LocalDateTime.now().minusHours(24);
        List<WebhookAlertEvent> history = Collections.singletonList(testAlert);
        when(alertRepository.findRecentByTenant(testTenantId, since)).thenReturn(history);

        // Act
        List<WebhookAlertEvent> result = alertService.getAlertHistory(testTenantId, 24);

        // Assert
        assertThat(result).hasSize(1);
    }

    // =====================================================
    // STATISTICS TESTS
    // =====================================================

    @Test
    @DisplayName("Should calculate alert statistics correctly")
    void testGetAlertStats_Success() {
        // Arrange
        WebhookAlertEvent criticalAlert = testAlert;
        WebhookAlertEvent warningAlert = WebhookAlertEvent.builder()
                .severity(WebhookAlertEvent.AlertSeverity.WARNING)
                .alertType(WebhookAlertEvent.AlertType.HIGH_FAILURE_RATE)
                .build();

        List<WebhookAlertEvent> alerts = Arrays.asList(criticalAlert, warningAlert);
        when(alertRepository.findActiveAlerts()).thenReturn(alerts);

        // Act
        var stats = alertService.getAlertStats();

        // Assert
        assertThat(stats).isNotNull();
        assertThat(stats.getTotalActive()).isGreaterThanOrEqualTo(0);
        assertThat(stats.getCriticalCount()).isGreaterThanOrEqualTo(0);
        assertThat(stats.getWarningCount()).isGreaterThanOrEqualTo(0);
    }

    // =====================================================
    // ERROR HANDLING TESTS
    // =====================================================

    @Test
    @DisplayName("Should handle database error alert creation")
    void testCreateDatabaseErrorAlert_Success() {
        // Arrange
        when(alertRepository.findRecentUnresolvedSameType(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(alertRepository.save(any(WebhookAlertEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        alertService.createDatabaseErrorAlert(testTenantId, "Connection timeout");

        // Assert
        ArgumentCaptor<WebhookAlertEvent> captor = ArgumentCaptor.forClass(WebhookAlertEvent.class);
        verify(alertRepository).save(captor.capture());
        assertThat(captor.getValue().getAlertType())
                .isEqualTo(WebhookAlertEvent.AlertType.DATABASE_ERROR);
        assertThat(captor.getValue().getSeverity())
                .isEqualTo(WebhookAlertEvent.AlertSeverity.CRITICAL);
    }

    // =====================================================
    // EDGE CASE TESTS
    // =====================================================

    @Test
    @DisplayName("Should handle null metrics in alert creation")
    void testCreateAlert_NullMetrics() {
        // Arrange
        when(alertRepository.findRecentUnresolvedSameType(any(), any(), any()))
                .thenReturn(Collections.emptyList());
        when(alertRepository.save(any(WebhookAlertEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        // Act
        WebhookAlertEvent result = alertService.createAlert(
                WebhookAlertEvent.AlertType.CIRCUIT_BREAKER_OPENED,
                WebhookAlertEvent.AlertSeverity.CRITICAL,
                testTenantId,
                "Test alert",
                null
        );

        // Assert
        assertThat(result).isNotNull();
        assertThat(result.getMetrics()).isNotNull();
    }

    @Test
    @DisplayName("Should handle zero failure count in rate check")
    void testCheckFailureRate_ZeroEvents() {
        // Act & Assert - should not throw exception
        alertService.checkFailureRate(testTenantId, 0, 0);
        verify(alertRepository, never()).save(any());
    }

    @Test
    @DisplayName("Should handle null last processed time")
    void testCheckProcessingStalled_NullTime() {
        // Act & Assert - should not throw exception
        alertService.checkProcessingStalled(testTenantId, null);
        verify(alertRepository, never()).save(any());
    }
}
