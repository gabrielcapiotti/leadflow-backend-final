package com.leadflow.backend.integration.billing;

import com.leadflow.backend.entities.StripeEventLog;
import com.leadflow.backend.entities.StripeEventLog.EventProcessingStatus;
import com.leadflow.backend.repository.StripeEventLogRepository;
import com.stripe.model.Event;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests to verify multi-tenant webhook isolation.
 * Ensures that webhooks for different tenants don't interfere with each other.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestPropertySource(properties = {
    "multitenancy.enabled=true",
    "jwt.secret=0123456789abcdef0123456789abcdef"
})
public class MultiTenantWebhookTest {

    @Autowired
    private StripeEventLogRepository stripeEventLogRepository;

    @Autowired
    private MockMvc mockMvc;

    private UUID tenantId1;
    private UUID tenantId2;
    private String customerId1;
    private String customerId2;

    @BeforeEach
    public void setUp() {
        // Create two unique tenant IDs
        tenantId1 = UUID.randomUUID();
        tenantId2 = UUID.randomUUID();
        customerId1 = "cus_" + UUID.randomUUID().toString().substring(0, 8);
        customerId2 = "cus_" + UUID.randomUUID().toString().substring(0, 8);

        // Clear existing events
        stripeEventLogRepository.deleteAll();
    }

    @Test
    public void shouldIsolatePendingEventsByTenant() {
        // Arrange: Create events for two different tenants
        StripeEventLog event1 = StripeEventLog.builder()
            .eventId("evt_tenant1_" + System.currentTimeMillis())
            .tenantId(tenantId1)
            .customerId(customerId1)
            .eventType("charge.succeeded")
            .payload("{}")
            .status(EventProcessingStatus.PENDING)
            .retryCount(0)
            .maxRetries(5)
            .nextRetryAt(LocalDateTime.now())
            .build();

        StripeEventLog event2 = StripeEventLog.builder()
            .eventId("evt_tenant2_" + System.currentTimeMillis())
            .tenantId(tenantId2)
            .customerId(customerId2)
            .eventType("invoice.payment_succeeded")
            .payload("{}")
            .status(EventProcessingStatus.PENDING)
            .retryCount(0)
            .maxRetries(5)
            .nextRetryAt(LocalDateTime.now())
            .build();

        stripeEventLogRepository.saveAll(List.of(event1, event2));

        // Act: Query pending events for tenant 1
        List<StripeEventLog> tenant1Events = stripeEventLogRepository
            .findPendingRetriesByTenant(tenantId1, EventProcessingStatus.PENDING);

        // Assert: Only tenant 1's events should be returned
        assertThat(tenant1Events)
            .hasSize(1)
            .allMatch(e -> e.getTenantId().equals(tenantId1))
            .allMatch(e -> e.getCustomerId().equals(customerId1));

        // Act: Query pending events for tenant 2
        List<StripeEventLog> tenant2Events = stripeEventLogRepository
            .findPendingRetriesByTenant(tenantId2, EventProcessingStatus.PENDING);

        // Assert: Only tenant 2's events should be returned
        assertThat(tenant2Events)
            .hasSize(1)
            .allMatch(e -> e.getTenantId().equals(tenantId2))
            .allMatch(e -> e.getCustomerId().equals(customerId2));
    }

    @Test
    public void shouldNotCrossPolluteBetweenTenants() {
        // Arrange: Create multiple events across different statuses
        StripeEventLog pending1 = createEvent(tenantId1, "evt_p1_" + System.currentTimeMillis(), 
            EventProcessingStatus.PENDING, customerId1);
        StripeEventLog success1 = createEvent(tenantId1, "evt_s1_" + System.currentTimeMillis(), 
            EventProcessingStatus.SUCCESS, customerId1);
        StripeEventLog pending2 = createEvent(tenantId2, "evt_p2_" + System.currentTimeMillis(), 
            EventProcessingStatus.PENDING, customerId2);
        StripeEventLog retry2 = createEvent(tenantId2, "evt_r2_" + System.currentTimeMillis(), 
            EventProcessingStatus.RETRY_PENDING, customerId2);

        stripeEventLogRepository.saveAll(List.of(pending1, success1, pending2, retry2));

        // Act: Count pending events by tenant
        long tenant1PendingCount = stripeEventLogRepository
            .countByTenantIdAndStatus(tenantId1, EventProcessingStatus.PENDING);
        long tenant2PendingCount = stripeEventLogRepository
            .countByTenantIdAndStatus(tenantId2, EventProcessingStatus.PENDING);

        // Assert: Counts should be isolated
        assertThat(tenant1PendingCount).isEqualTo(1);
        assertThat(tenant2PendingCount).isEqualTo(1);

        // Act: Find events with multiple statuses per tenant
        List<StripeEventLog> tenant1All = stripeEventLogRepository
            .findByTenantIdAndStatuses(tenantId1, 
                List.of(EventProcessingStatus.PENDING, EventProcessingStatus.SUCCESS));
        List<StripeEventLog> tenant2All = stripeEventLogRepository
            .findByTenantIdAndStatuses(tenantId2, 
                List.of(EventProcessingStatus.PENDING, EventProcessingStatus.RETRY_PENDING));

        // Assert: Should only return events for respective tenant
        assertThat(tenant1All)
            .hasSize(2)
            .allMatch(e -> e.getTenantId().equals(tenantId1));
        assertThat(tenant2All)
            .hasSize(2)
            .allMatch(e -> e.getTenantId().equals(tenantId2));
    }

    @Test
    public void shouldFindEventByTenantAndCustomerId() {
        // Arrange: Create events for same customer with different tenants
        StripeEventLog event1 = createEvent(tenantId1, "evt_1_" + System.currentTimeMillis(), 
            EventProcessingStatus.PENDING, customerId1);
        StripeEventLog event2 = createEvent(tenantId2, "evt_2_" + System.currentTimeMillis(), 
            EventProcessingStatus.PENDING, customerId1); // Same customer ID, different tenant

        stripeEventLogRepository.saveAll(List.of(event1, event2));

        // Act: Query by tenant and customer ID
        List<StripeEventLog> tenant1CustomerEvents = stripeEventLogRepository
            .findByTenantIdAndCustomerId(tenantId1, customerId1);

        // Assert: Should only return events for tenant1, even though customer ID matches
        assertThat(tenant1CustomerEvents)
            .hasSize(1)
            .allMatch(e -> e.getTenantId().equals(tenantId1))
            .allMatch(e -> e.getCustomerId().equals(customerId1));
    }

    @Test
    public void shouldProgressEventsIndependentlyByTenant() {
        // Arrange: Create events at the same time
        LocalDateTime now = LocalDateTime.now();
        StripeEventLog event1 = StripeEventLog.builder()
            .eventId("evt_t1_" + System.currentTimeMillis())
            .tenantId(tenantId1)
            .customerId(customerId1)
            .eventType("charge.succeeded")
            .payload("{}")
            .status(EventProcessingStatus.RETRY_PENDING)
            .retryCount(2)
            .maxRetries(5)
            .nextRetryAt(now.plusSeconds(8)) // Retry in 8 seconds
            .build();

        StripeEventLog event2 = StripeEventLog.builder()
            .eventId("evt_t2_" + System.currentTimeMillis())
            .tenantId(tenantId2)
            .customerId(customerId2)
            .eventType("charge.succeeded")
            .payload("{}")
            .status(EventProcessingStatus.RETRY_PENDING)
            .retryCount(1)
            .maxRetries(5)
            .nextRetryAt(now.plusSeconds(4)) // Retry in 4 seconds (earlier)
            .build();

        stripeEventLogRepository.saveAll(List.of(event1, event2));

        // Act: Update only tenant2's event as processed
        event2.setStatus(EventProcessingStatus.SUCCESS);
        event2.setProcessedAt(LocalDateTime.now());
        stripeEventLogRepository.save(event2);

        // Assert: Tenant1's event should remain unchanged
        StripeEventLog retrievedEvent1 = stripeEventLogRepository.findById(event1.getId()).orElseThrow();
        assertThat(retrievedEvent1.getStatus()).isEqualTo(EventProcessingStatus.RETRY_PENDING);
        assertThat(retrievedEvent1.getRetryCount()).isEqualTo(2);
        assertThat(retrievedEvent1.getProcessedAt()).isNull();

        // Assert: Tenant2's event should be updated
        StripeEventLog retrievedEvent2 = stripeEventLogRepository.findById(event2.getId()).orElseThrow();
        assertThat(retrievedEvent2.getStatus()).isEqualTo(EventProcessingStatus.SUCCESS);
        assertThat(retrievedEvent2.getProcessedAt()).isNotNull();
    }

    @Test
    public void shouldCountWebhooksByTenantIndependently() {
        // Arrange: Create 3 events for tenant1, 2 for tenant2
        for (int i = 0; i < 3; i++) {
            createEvent(tenantId1, "evt_t1_" + i + "_" + System.currentTimeMillis(), 
                EventProcessingStatus.PENDING, customerId1);
        }
        for (int i = 0; i < 2; i++) {
            createEvent(tenantId2, "evt_t2_" + i + "_" + System.currentTimeMillis(), 
                EventProcessingStatus.PENDING, customerId2);
        }

        // Act: Count total events per tenant
        long count1 = stripeEventLogRepository.countByTenantIdAndStatus(tenantId1, EventProcessingStatus.PENDING);
        long count2 = stripeEventLogRepository.countByTenantIdAndStatus(tenantId2, EventProcessingStatus.PENDING);

        // Assert: Counts should be independent
        assertThat(count1).isEqualTo(3);
        assertThat(count2).isEqualTo(2);
    }

    // Helper method
    private StripeEventLog createEvent(UUID tenantId, String eventId, EventProcessingStatus status, String customerId) {
        return StripeEventLog.builder()
            .eventId(eventId)
            .tenantId(tenantId)
            .customerId(customerId)
            .eventType("charge.succeeded")
            .payload("{}")
            .status(status)
            .retryCount(0)
            .maxRetries(5)
            .nextRetryAt(LocalDateTime.now())
            .build();
    }
}
