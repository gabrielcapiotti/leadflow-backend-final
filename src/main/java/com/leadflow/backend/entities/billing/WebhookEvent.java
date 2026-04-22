package com.leadflow.backend.entities.billing;

import com.leadflow.backend.multitenancy.context.TenantContext;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "webhook_events", indexes = {
        @Index(name = "idx_webhook_event_id", columnList = "event_id", unique = true),
        @Index(name = "idx_webhook_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_webhook_tenant_received", columnList = "tenant_id, received_at")
})
public class WebhookEvent {

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "received_at", nullable = false, updatable = false)
    private Instant receivedAt;

    public WebhookEvent() {}

    public WebhookEvent(String eventId, UUID tenantId) {
        this.eventId = eventId;
        this.tenantId = tenantId;
    }

    @PrePersist
    public void onCreate() {
        if (this.receivedAt == null) {
            this.receivedAt = Instant.now();
        }
        if (this.tenantId == null) {
            this.tenantId = TenantContext.requireTenant();
        }
    }

    public String getEventId() {
        return eventId;
    }

    public void setEventId(String eventId) {
        this.eventId = eventId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public Instant getReceivedAt() {
        return receivedAt;
    }

    public void setReceivedAt(Instant receivedAt) {
        this.receivedAt = receivedAt;
    }
}
