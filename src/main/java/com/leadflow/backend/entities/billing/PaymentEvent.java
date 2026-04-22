package com.leadflow.backend.entities.billing;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import com.leadflow.backend.multitenancy.context.TenantContext;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payment_events", uniqueConstraints = {
        @UniqueConstraint(name = "uq_payment_event_provider_id", columnNames = "provider_event_id")
}, indexes = {
    @Index(name = "idx_payment_events_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_payment_events_provider_tenant", columnList = "provider, tenant_id")
})
public class PaymentEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "provider_event_id", nullable = false)
    private String providerEventId;

    @Column(nullable = false)
    private String provider;

    @Column(nullable = false, updatable = false)
    private Instant processedAt = Instant.now();

    @PrePersist
    public void prePersist() {
        if (tenantId == null) {
            tenantId = TenantContext.requireTenant();
        }
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public String getProviderEventId() {
        return providerEventId;
    }

    public void setProviderEventId(String providerEventId) {
        this.providerEventId = providerEventId;
    }

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Instant getProcessedAt() {
        return processedAt;
    }
}