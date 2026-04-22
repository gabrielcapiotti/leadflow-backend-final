package com.leadflow.backend.entities.vendor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import org.hibernate.annotations.Filter;
import com.leadflow.backend.multitenancy.context.TenantContext;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "subscription_history", schema = "public", indexes = {
    @Index(name = "idx_subscription_history_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_subscription_history_vendor_tenant", columnList = "vendor_id, tenant_id")
})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class SubscriptionHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID vendorId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SubscriptionStatus newStatus;

    @Column(nullable = false)
    private Instant changedAt;

    private String reason;

    private String externalEventId;

    public SubscriptionHistory() {}

    @PrePersist
    public void onCreate() {
        if (tenantId == null) {
            tenantId = TenantContext.requireTenant();
        }
        if (this.changedAt == null) {
            this.changedAt = Instant.now();
        }
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public UUID getVendorId() {
        return vendorId;
    }

    public void setVendorId(UUID vendorId) {
        this.vendorId = vendorId;
    }

    public SubscriptionStatus getPreviousStatus() {
        return previousStatus;
    }

    public void setPreviousStatus(SubscriptionStatus previousStatus) {
        this.previousStatus = previousStatus;
    }

    public SubscriptionStatus getNewStatus() {
        return newStatus;
    }

    public void setNewStatus(SubscriptionStatus newStatus) {
        this.newStatus = newStatus;
    }

    public Instant getChangedAt() {
        return changedAt;
    }

    public void setChangedAt(Instant changedAt) {
        this.changedAt = changedAt;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getExternalEventId() {
        return externalEventId;
    }

    public void setExternalEventId(String externalEventId) {
        this.externalEventId = externalEventId;
    }
}
