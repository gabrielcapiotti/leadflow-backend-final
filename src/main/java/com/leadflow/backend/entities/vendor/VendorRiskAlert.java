package com.leadflow.backend.entities.vendor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
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
@Table(name = "vendor_risk_alerts", indexes = {
    @Index(name = "idx_vendor_risk_alert_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_vendor_risk_alert_vendor_tenant", columnList = "vendor_id, tenant_id")
})
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class VendorRiskAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID vendorId;

    @Column(nullable = false)
    private int score;

    @Column(nullable = false)
    private String riskLevel;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @Column(nullable = false)
    private boolean resolved = false;

    @PrePersist
    public void onCreate() {
        if (tenantId == null) {
            tenantId = TenantContext.requireTenant();
        }
        this.createdAt = Instant.now();
    }

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public UUID getVendorId() {
        return vendorId;
    }

    public int getScore() {
        return score;
    }

    public String getRiskLevel() {
        return riskLevel;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public boolean isResolved() {
        return resolved;
    }

    public void setVendorId(UUID vendorId) {
        this.vendorId = vendorId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public void setScore(int score) {
        this.score = score;
    }

    public void setRiskLevel(String riskLevel) {
        this.riskLevel = riskLevel;
    }

    public void setResolved(boolean resolved) {
        this.resolved = resolved;
    }
}