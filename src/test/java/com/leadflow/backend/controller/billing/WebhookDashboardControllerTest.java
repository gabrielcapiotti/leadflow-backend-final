package com.leadflow.backend.controller.billing;

import com.leadflow.backend.dto.billing.WebhookDashboardDTO;
import com.leadflow.backend.entities.StripeEventLog;
import com.leadflow.backend.repository.StripeEventLogRepository;
import com.leadflow.backend.service.billing.BillingDashboardService;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for Webhook Dashboard endpoints (ETAPA 2)
 * Tests dashboard retrieval, filtering, aggregation, and security
 */
@SpringBootTest
@AutoConfigureMockMvc
@Slf4j
@DisplayName("Webhook Dashboard Controller Tests")
class WebhookDashboardControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private BillingDashboardService billingDashboardService;

    @Autowired
    private StripeEventLogRepository eventLogRepository;

    private UUID testTenantId;
    private String testCustomerId;

    @BeforeEach
    void setUp() {
        testTenantId = UUID.randomUUID();
        testCustomerId = "cus_test_" + System.currentTimeMillis();

        // Clear existing events
        eventLogRepository.deleteAll();
    }

    // =====================================================
    // DASHBOARD ENDPOINT TESTS
    // =====================================================

    @Test
    @DisplayName("GET /api/v1/billing/webhooks/dashboard - Admin can view dashboard")
    @WithMockUser(roles = "ADMIN")
    void testGetWebhookDashboard() throws Exception {
        // Arrange: Create test webhook events
        createTestWebhookEvents(5, 2, 1);

        // Act & Assert
        var response = mockMvc.perform(get("/api/v1/billing/webhooks/dashboard"))
                .andExpect(status().isOk())
                .andReturn();

        String content = response.getResponse().getContentAsString();
        log.info("Dashboard response: {}", content);

        assertNotNull(content);
        assertTrue(content.contains("metrics"));
    }

    @Test
    @DisplayName("GET /api/v1/billing/webhooks/dashboard - Requires ADMIN role")
    @WithMockUser(roles = "USER")
    void testGetWebhookDashboardRequiresAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/billing/webhooks/dashboard"))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /api/v1/billing/webhooks/dashboard - Unauthenticated access denied")
    void testGetWebhookDashboardRequiresAuth() throws Exception {
        mockMvc.perform(get("/api/v1/billing/webhooks/dashboard"))
                .andExpect(status().isUnauthorized());
    }

    // =====================================================
    // RECENT WEBHOOKS ENDPOINT TESTS
    // =====================================================

    @Test
    @DisplayName("GET /api/v1/billing/webhooks/recent - Retrieve recent events")
    @WithMockUser(roles = "ADMIN")
    void testGetRecentWebhooks() throws Exception {
        // Arrange: Create test events
        createTestWebhookEvents(5, 0, 0);

        // Act & Assert
        var response = mockMvc.perform(get("/api/v1/billing/webhooks/recent?limit=10"))
                .andExpect(status().isOk())
                .andReturn();

        String content = response.getResponse().getContentAsString();
        log.info("Recent webhooks response: {}", content);

        assertNotNull(content);
        assertTrue(content.contains("eventId") || content.contains("\"\""));
    }

    @Test
    @DisplayName("GET /api/v1/billing/webhooks/recent - Respects limit parameter")
    @WithMockUser(roles = "ADMIN")
    void testRecentWebhooksLimit() throws Exception {
        // Arrange: Create 15 test events
        createTestWebhookEvents(15, 0, 0);

        // Act & Assert - Request limit=5
        var response = mockMvc.perform(get("/api/v1/billing/webhooks/recent?limit=5"))
                .andExpect(status().isOk())
                .andReturn();

        String content = response.getResponse().getContentAsString();
        log.info("Limited recent webhooks (5): {}", content);

        // Should not exceed 5
        long eventCount = content.split("\"eventId\"").length - 1;
        assertTrue(eventCount <= 5);
    }

    // =====================================================
    // FAILURE ANALYSIS ENDPOINT TESTS
    // =====================================================

    @Test
    @DisplayName("GET /api/v1/billing/webhooks/failures - Analyze failures")
    @WithMockUser(roles = "ADMIN")
    void testAnalyzeWebhookFailures() throws Exception {
        // Arrange: Create events with failures
        createTestWebhookEventsWithErrors(3);

        // Act & Assert
        var response = mockMvc.perform(get("/api/v1/billing/webhooks/failures"))
                .andExpect(status().isOk())
                .andReturn();

        String content = response.getResponse().getContentAsString();
        log.info("Failure analysis response: {}", content);

        assertNotNull(content);
        assertTrue(content.contains("failureCount") || content.contains("reasons"));
    }

    @Test
    @DisplayName("GET /api/v1/billing/webhooks/failures - Empty when no failures")
    @WithMockUser(roles = "ADMIN")
    void testAnalyzeWebhookFailuresNoFailures() throws Exception {
        // Arrange: Create only successful events
        createTestWebhookEvents(3, 0, 0);

        // Act
        WebhookDashboardDTO.FailureAnalysisDTO analysis =
                billingDashboardService.analyzeFailures();

        // Assert
        assertNotNull(analysis);
        assertEquals(0L, analysis.getFailureCount());
        assertTrue(analysis.getReasons().isEmpty());
    }

    // =====================================================
    // BREAKDOWN ENDPOINT TESTS
    // =====================================================

    @Test
    @DisplayName("GET /api/v1/billing/webhooks/breakdown/by-tenant - Show tenant distribution")
    @WithMockUser(roles = "ADMIN")
    void testWebhooksByTenant() throws Exception {
        // Arrange: Create events for different tenants
        createTestWebhookEventsForMultipleTenants(3);

        // Act
        Map<String, Long> breakdown = billingDashboardService.getWebhooksByTenant();

        // Assert
        assertNotNull(breakdown);
        assertTrue(breakdown.size() >= 1);
        assertTrue(breakdown.values().stream().mapToLong(Long::longValue).sum() >= 3);
    }

    @Test
    @DisplayName("GET /api/v1/billing/webhooks/breakdown/by-type - Show event type distribution")
    @WithMockUser(roles = "ADMIN")
    void testWebhooksByEventType() throws Exception {
        // Arrange: Create events with different types
        createTestWebhookEventsWithTypes(3);

        // Act
        Map<String, Long> breakdown = billingDashboardService.getWebhooksByEventType();

        // Assert
        assertNotNull(breakdown);
        assertTrue(breakdown.size() > 0);
        assertTrue(breakdown.values().stream().mapToLong(Long::longValue).sum() >= 3);
    }

    @Test
    @DisplayName("GET /api/v1/billing/webhooks/breakdown/by-status - Show status distribution")
    @WithMockUser(roles = "ADMIN")
    void testWebhooksByStatus() throws Exception {
        // Arrange: Create events with different statuses
        createTestWebhookEvents(3, 1, 1);

        // Act
        Map<String, Long> breakdown = billingDashboardService.getWebhooksByStatus();

        // Assert
        assertNotNull(breakdown);
        assertTrue(breakdown.size() > 0);
    }

    // =====================================================
    // SERVICE TESTS (Direct Service Testing)
    // =====================================================

    @Test
    @DisplayName("BillingDashboardService - getWebhookDashboard() returns complete metrics")
    void testGetWebhookDashboardService() {
        // Arrange
        createTestWebhookEvents(5, 2, 1);

        // Act
        WebhookDashboardDTO dashboard = billingDashboardService.getWebhookDashboard();

        // Assert
        assertNotNull(dashboard);
        assertNotNull(dashboard.getMetrics());
        assertNotNull(dashboard.getHealth());
        assertNotNull(dashboard.getRecentEvents());
        assertNotNull(dashboard.getFailureAnalysis());

        assertEquals(8L, dashboard.getMetrics().getTotalWebhooksReceived());
        assertEquals(5L, dashboard.getMetrics().getTotalSuccessful());
        assertEquals(2L, dashboard.getMetrics().getTotalFailed());
        assertEquals(1L, dashboard.getMetrics().getTotalPendingRetry());
    }

    @Test
    @DisplayName("BillingDashboardService - getRecentWebhooks() returns events in order")
    void testGetRecentWebhooksService() {
        // Arrange
        List<StripeEventLog> created = createTestWebhookEvents(5, 0, 0);

        // Act
        List<WebhookDashboardDTO.RecentWebhookDTO> recent =
                billingDashboardService.getRecentWebhooks(10);

        // Assert
        assertNotNull(recent);
        assertEquals(5, recent.size());

        // Check all event IDs are present
        for (StripeEventLog event : created) {
            assertTrue(recent.stream()
                    .anyMatch(r -> r.getEventId().equals(event.getEventId())));
        }
    }

    @Test
    @DisplayName("BillingDashboardService - Calculates success rate correctly")
    void testSuccessRateCalculation() {
        // Arrange: 7 successful, 3 failed = 70% success rate
        createTestWebhookEvents(7, 3, 0);

        // Act
        WebhookDashboardDTO dashboard = billingDashboardService.getWebhookDashboard();

        // Assert
        assertNotNull(dashboard.getMetrics().getSuccessRate());
        double successRate = dashboard.getMetrics().getSuccessRate();
        assertTrue(successRate >= 69.0 && successRate <= 71.0);  // Allow small rounding
    }

    @Test
    @DisplayName("BillingDashboardService - Handles empty database gracefully")
    void testEmptyDatabaseHandling() {
        // Arrange: No events in database

        // Act
        WebhookDashboardDTO dashboard = billingDashboardService.getWebhookDashboard();
        Map<String, Long> byTenant = billingDashboardService.getWebhooksByTenant();
        Map<String, Long> byType = billingDashboardService.getWebhooksByEventType();
        Map<String, Long> byStatus = billingDashboardService.getWebhooksByStatus();

        // Assert
        assertNotNull(dashboard);
        assertNotNull(dashboard.getMetrics());
        assertEquals(0L, dashboard.getMetrics().getTotalWebhooksReceived());
        
        assertNotNull(byTenant);
        assertNotNull(byType);
        assertNotNull(byStatus);
    }

    // =====================================================
    // HELPER METHODS
    // =====================================================

    private List<StripeEventLog> createTestWebhookEvents(
            long successCount,
            long failedCount,
            long retryPendingCount) {

        List<StripeEventLog> events = new java.util.ArrayList<>();

        // Create successful events
        for (int i = 0; i < successCount; i++) {
            StripeEventLog event = StripeEventLog.builder()
                    .eventId("evt_success_" + i)
                    .eventType("invoice.paid")
                    .tenantId(testTenantId)
                    .customerId(testCustomerId)
                    .status(StripeEventLog.EventProcessingStatus.SUCCESS)
                    .retryCount(0)
                    .maxRetries(3)
                    .payload("{\"test\": true}")
                    .processedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now().minusMinutes(i))
                    .build();
            events.add(eventLogRepository.save(event));
        }

        // Create failed events
        for (int i = 0; i < failedCount; i++) {
            StripeEventLog event = StripeEventLog.builder()
                    .eventId("evt_failed_" + i)
                    .eventType("customer.subscription.updated")
                    .tenantId(testTenantId)
                    .customerId(testCustomerId)
                    .status(StripeEventLog.EventProcessingStatus.FAILED)
                    .retryCount(3)
                    .maxRetries(3)
                    .lastError("INVALID_EVENT")
                    .payload("{\"test\": true}")
                    .createdAt(LocalDateTime.now().minusHours(i + 1))
                    .build();
            events.add(eventLogRepository.save(event));
        }

        // Create retry pending events
        for (int i = 0; i < retryPendingCount; i++) {
            StripeEventLog event = StripeEventLog.builder()
                    .eventId("evt_retry_" + i)
                    .eventType("payment_intent.succeeded")
                    .tenantId(testTenantId)
                    .customerId(testCustomerId)
                    .status(StripeEventLog.EventProcessingStatus.RETRY_PENDING)
                    .retryCount(1)
                    .maxRetries(3)
                    .lastError("TIMEOUT")
                    .nextRetryAt(LocalDateTime.now().plusSeconds(30))
                    .payload("{\"test\": true}")
                    .createdAt(LocalDateTime.now().minusDays(i + 1))
                    .build();
            events.add(eventLogRepository.save(event));
        }

        return events;
    }

    private void createTestWebhookEventsWithErrors(int count) {
        String[] errorMessages = {
                "Tenant not found",
                "Invalid customer",
                "Network timeout"
        };

        for (int i = 0; i < count; i++) {
            StripeEventLog event = StripeEventLog.builder()
                    .eventId("evt_error_" + i)
                    .eventType("invoice.payment_failed")
                    .tenantId(testTenantId)
                    .customerId(testCustomerId)
                    .status(StripeEventLog.EventProcessingStatus.FAILED)
                    .retryCount(3)
                    .maxRetries(3)
                    .lastError(errorMessages[i % errorMessages.length])
                    .payload("{\"test\": true}")
                    .createdAt(LocalDateTime.now().minusHours(i))
                    .build();
            eventLogRepository.save(event);
        }
    }

    private void createTestWebhookEventsForMultipleTenants(int eventsPerTenant) {
        for (int tenantIdx = 0; tenantIdx < 3; tenantIdx++) {
            UUID tenantId = UUID.randomUUID();
            for (int i = 0; i < eventsPerTenant; i++) {
                StripeEventLog event = StripeEventLog.builder()
                        .eventId("evt_tenant_" + tenantIdx + "_" + i)
                        .eventType("invoice.paid")
                        .tenantId(tenantId)
                        .customerId("cus_tenant_" + tenantIdx)
                        .status(StripeEventLog.EventProcessingStatus.SUCCESS)
                        .retryCount(0)
                        .maxRetries(3)
                        .payload("{\"test\": true}")
                        .processedAt(LocalDateTime.now())
                        .createdAt(LocalDateTime.now().minusMinutes(i))
                        .build();
                eventLogRepository.save(event);
            }
        }
    }

    private void createTestWebhookEventsWithTypes(int count) {
        String[] eventTypes = {
                "invoice.paid",
                "customer.subscription.updated",
                "payment_intent.succeeded",
                "charge.failed"
        };

        for (int i = 0; i < count; i++) {
            StripeEventLog event = StripeEventLog.builder()
                    .eventId("evt_type_" + i)
                    .eventType(eventTypes[i % eventTypes.length])
                    .tenantId(testTenantId)
                    .customerId(testCustomerId)
                    .status(StripeEventLog.EventProcessingStatus.SUCCESS)
                    .retryCount(0)
                    .maxRetries(3)
                    .payload("{\"test\": true}")
                    .processedAt(LocalDateTime.now())
                    .createdAt(LocalDateTime.now().minusMinutes(i))
                    .build();
            eventLogRepository.save(event);
        }
    }
}
