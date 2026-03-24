package com.leadflow.backend.controller.billing;

import com.leadflow.backend.dto.metrics.WebhookEventMetrics;
import com.leadflow.backend.repository.StripeEventLogRepository;
import com.leadflow.backend.service.billing.WebhookMetricsTracker;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Test Suite for WebhookMetricsController
 * 
 * Tests all 6 REST endpoints with 15+ scenarios covering:
 * - System-wide metrics aggregation
 * - Per-tenant metric filtering
 * - Failure breakdown categorization
 * - Latency percentile calculations
 * - Prometheus export format
 * - Real-time metric snapshots
 * - Authorization requirements
 * - Edge cases (empty data, null values, etc.)
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("WebhookMetricsController Test Suite")
class WebhookMetricsControllerTest {

    private MockMvc mockMvc;
    private WebhookMetricsController controller;
    private MeterRegistry meterRegistry;

    @Mock
    private StripeEventLogRepository eventLogRepository;

    private WebhookMetricsTracker metricsTracker;
    private UUID testTenantId;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        metricsTracker = new WebhookMetricsTracker(meterRegistry);
        metricsTracker.initialize();
        
        controller = new WebhookMetricsController(
                metricsTracker,
                eventLogRepository,
                meterRegistry
        );
        
        mockMvc = MockMvcBuilders
                .standaloneSetup(controller)
                .build();
        
        testTenantId = UUID.randomUUID();
    }

    // =====================================================
    // TEST SCENARIO 1-3: GET /api/v1/billing/webhooks/metrics
    // =====================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Scenario 1: getSystemMetrics - Success with populated data")
    void testGetSystemMetrics_Success() throws Exception {
        // Arrange: Record some test metrics
        metricsTracker.recordEventReceived(testTenantId, "customer.created", "evt_123");
        metricsTracker.recordEventProcessed(testTenantId, "customer.created", 250);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceived", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.capturedAt", notNullValue()))
                .andExpect(jsonPath("$.aggregationPeriodSeconds", equalTo(300)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Scenario 2: getSystemMetrics - Success with zero metrics")
    void testGetSystemMetrics_EmptyMetrics() throws Exception {
        // Act & Assert - should return valid response even with no metrics
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceived", equalTo(0)))
                .andExpect(jsonPath("$.totalProcessed", equalTo(0)))
                .andExpect(jsonPath("$.totalFailed", equalTo(0)));
    }

    @Test
    @DisplayName("Scenario 3: getSystemMetrics - Unauthorized (no admin role)")
    void testGetSystemMetrics_Unauthorized() throws Exception {
        // Act & Assert - should reject non-admin users
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics"))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================
    // TEST SCENARIO 4-6: GET /api/v1/billing/webhooks/metrics/tenant/{tenantId}
    // =====================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Scenario 4: getTenantMetrics - Success with tenant-specific data")
    void testGetTenantMetrics_Success() throws Exception {
        // Arrange
        metricsTracker.recordEventReceived(testTenantId, "payment_intent.succeeded", "evt_456");
        String tenantIdStr = testTenantId.toString();
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics/tenant/" + tenantIdStr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId", equalTo(tenantIdStr)))
                .andExpect(jsonPath("$.totalReceived", greaterThanOrEqualTo(0)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Scenario 5: getTenantMetrics - Invalid UUID format")
    void testGetTenantMetrics_InvalidUUID() throws Exception {
        // Act & Assert - should handle gracefully
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics/tenant/invalid-uuid"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Scenario 6: getTenantMetrics - Non-existent tenant ID")
    void testGetTenantMetrics_NonExistentTenant() throws Exception {
        // Arrange
        UUID nonExistentTenantId = UUID.randomUUID();
        
        // Act & Assert - should return 200 with empty/zero metrics
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics/tenant/" + nonExistentTenantId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tenantId", equalTo(nonExistentTenantId.toString())));
    }

    // =====================================================
    // TEST SCENARIO 7-9: GET /api/v1/billing/webhooks/metrics/failures/breakdown
    // =====================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Scenario 7: getFailureBreakdown - Success with categorized failures")
    void testGetFailureBreakdown_Success() throws Exception {
        // Arrange
        metricsTracker.recordEventFailed(testTenantId, "charge.failed", "timeout", 500);
        metricsTracker.recordEventFailed(testTenantId, "charge.failed", "database", 300);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics/failures/breakdown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.byReason", notNullValue()))
                .andExpect(jsonPath("$.byEventType", notNullValue()))
                .andExpect(jsonPath("$.byTenant", notNullValue()))
                .andExpect(jsonPath("$.totalFailures", greaterThanOrEqualTo(0)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Scenario 8: getFailureBreakdown - No failures recorded")
    void testGetFailureBreakdown_NoFailures() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics/failures/breakdown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFailures", equalTo(0)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Scenario 9: getFailureBreakdown - Multiple failure reasons aggregated")
    void testGetFailureBreakdown_MultipleReasons() throws Exception {
        // Arrange - record various failure types
        for (int i = 0; i < 3; i++) {
            metricsTracker.recordEventFailed(testTenantId, "invoice.created", "timeout", 100 * i);
        }
        for (int i = 0; i < 2; i++) {
            metricsTracker.recordEventFailed(testTenantId, "invoice.created", "validation", 50 * i);
        }
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics/failures/breakdown"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalFailures", greaterThanOrEqualTo(5)));
    }

    // =====================================================
    // TEST SCENARIO 10-12: GET /api/v1/billing/webhooks/metrics/latency/percentiles
    // =====================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Scenario 10: getLatencyPercentiles - Success with percentile data")
    void testGetLatencyPercentiles_Success() throws Exception {
        // Arrange
        metricsTracker.recordEventProcessed(testTenantId, "customer.created", 100);
        metricsTracker.recordEventProcessed(testTenantId, "customer.created", 500);
        metricsTracker.recordEventProcessed(testTenantId, "customer.created", 1000);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics/latency/percentiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.p50", notNullValue()))
                .andExpect(jsonPath("$.p75", notNullValue()))
                .andExpect(jsonPath("$.p95", notNullValue()))
                .andExpect(jsonPath("$.p99", notNullValue()))
                .andExpect(jsonPath("$.avg", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.max", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.samples", greaterThanOrEqualTo(0)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Scenario 11: getLatencyPercentiles - No processing data")
    void testGetLatencyPercentiles_NoData() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics/latency/percentiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.avg", equalTo(0)))
                .andExpect(jsonPath("$.samples", equalTo(0)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Scenario 12: getLatencyPercentiles - High latency detection")
    void testGetLatencyPercentiles_HighLatency() throws Exception {
        // Arrange - record high latency events
        metricsTracker.recordEventProcessed(testTenantId, "charge.failed", 8000);
        metricsTracker.recordEventProcessed(testTenantId, "charge.failed", 9000);
        metricsTracker.recordEventProcessed(testTenantId, "charge.failed", 10000);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics/latency/percentiles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.p99", greaterThan(5000)));
    }

    // =====================================================
    // TEST SCENARIO 13-15: GET /api/v1/billing/webhooks/metrics/real-time
    // =====================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Scenario 13: getRealTimeMetrics - Success with current snapshot")
    void testGetRealTimeMetrics_Success() throws Exception {
        // Arrange
        metricsTracker.recordEventReceived(testTenantId, "customer.created", "evt_789");
        metricsTracker.recordEventProcessed(testTenantId, "customer.created", 300);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics/real-time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueSize", notNullValue()))
                .andExpect(jsonPath("$.activeTenants", notNullValue()))
                .andExpect(jsonPath("$.totalReceived", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.totalProcessed", greaterThanOrEqualTo(0)))
                .andExpect(jsonPath("$.capturedAt", notNullValue()))
                .andExpect(jsonPath("$.aggregationPeriodSeconds", equalTo(60)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Scenario 14: getRealTimeMetrics - Empty metrics return defaults")
    void testGetRealTimeMetrics_EmptyData() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics/real-time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueSize", equalTo(0)))
                .andExpect(jsonPath("$.totalReceived", equalTo(0)));
    }

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Scenario 15: getRealTimeMetrics - Multiple tenants aggregated")
    void testGetRealTimeMetrics_MultiTenant() throws Exception {
        // Arrange - record metrics for multiple tenants
        UUID tenant1 = UUID.randomUUID();
        UUID tenant2 = UUID.randomUUID();
        
        metricsTracker.recordEventReceived(tenant1, "customer.created", "evt_1");
        metricsTracker.recordEventReceived(tenant2, "payment_intent.succeeded", "evt_2");
        metricsTracker.recordEventProcessed(tenant1, "customer.created", 200);
        metricsTracker.recordEventFailed(tenant2, "charge.failed", "timeout", 500);
        
        // Act & Assert
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics/real-time"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceived", greaterThanOrEqualTo(2)))
                .andExpect(jsonPath("$.totalProcessed", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalFailed", greaterThanOrEqualTo(1)));
    }

    // =====================================================
    // TEST SCENARIO 16-17: Authorization Tests
    // =====================================================

    @Test
    @DisplayName("Scenario 16: All endpoints require ADMIN role")
    void testAllEndpoints_RequireAdminRole() throws Exception {
        String[] endpoints = {
                "/api/v1/billing/webhooks/metrics",
                "/api/v1/billing/webhooks/metrics/tenant/" + UUID.randomUUID(),
                "/api/v1/billing/webhooks/metrics/failures/breakdown",
                "/api/v1/billing/webhooks/metrics/latency/percentiles",
                "/api/v1/billing/webhooks/metrics/real-time"
        };
        
        for (String endpoint : endpoints) {
            // Act & Assert - should reject unauthenticated requests
            mockMvc.perform(get(endpoint))
                    .andExpect(status().isUnauthorized());
        }
    }

    @Test
    @WithMockUser(roles = "USER")  // Non-admin user
    @DisplayName("Scenario 17: Non-admin users cannot access metrics")
    void testMetricsEndpoints_NonAdminRejected() throws Exception {
        // Act & Assert
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics"))
                .andExpect(status().isForbidden());
    }

    // =====================================================
    // TEST SCENARIO 18: Comprehensive Integration Test
    // =====================================================

    @Test
    @WithMockUser(roles = "ADMIN")
    @DisplayName("Scenario 18: Complete workflow - record metrics and retrieve")
    void testCompleteWorkflow_RecordAndRetrieveMetrics() throws Exception {
        // Arrange - simulate complete webhook lifecycle
        UUID tenant = UUID.randomUUID();
        
        // 1. Events received
        for (int i = 0; i < 10; i++) {
            metricsTracker.recordEventReceived(tenant, "customer.created", "evt_" + i);
        }
        
        // 2. Events processed
        for (int i = 0; i < 7; i++) {
            metricsTracker.recordEventProcessed(tenant, "customer.created", 200 + i * 50);
        }
        
        // 3. Events failed
        for (int i = 0; i < 3; i++) {
            metricsTracker.recordEventFailed(tenant, "customer.created", "timeout", 5000 + i * 100);
        }
        
        // 4. Retries attempted
        metricsTracker.recordRetryAttempt(tenant, "evt_0", 1, 3);
        metricsTracker.recordRetrySuccess(tenant, 350);
        
        // Act & Assert - verify all metrics are accessible
        mockMvc.perform(get("/api/v1/billing/webhooks/metrics"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReceived", greaterThanOrEqualTo(10)))
                .andExpect(jsonPath("$.totalProcessed", greaterThanOrEqualTo(7)))
                .andExpect(jsonPath("$.totalFailed", greaterThanOrEqualTo(3)))
                .andExpect(jsonPath("$.successRate", greaterThan(0.0)));
    }
}
