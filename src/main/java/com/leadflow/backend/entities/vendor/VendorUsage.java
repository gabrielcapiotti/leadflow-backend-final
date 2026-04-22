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
@Table(name = "vendor_usage", schema = "public", indexes = {
    @Index(name = "idx_vendor_usage_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_vendor_usage_vendor_tenant", columnList = "vendor_id, tenant_id")
})
@Filter(
    name = "tenantFilter",
    condition = "tenant_id = :tenantId"
)
public class VendorUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "vendor_id", nullable = false)
    private UUID vendorId;

    @Enumerated(EnumType.STRING)
    @Column(name = "quota_type", nullable = false)
    private QuotaType quotaType;

    @Column(nullable = false)
    private int used = 0;

    @Column(name = "alert80_sent", nullable = false)
    private boolean alert80Sent = false;

    @Column(name = "alert100_sent", nullable = false)
    private boolean alert100Sent = false;

    @Column(name = "period_start", nullable = false)
    private Instant periodStart;

    @Column(name = "period_end", nullable = false)
    private Instant periodEnd;

    @PrePersist
    public void prePersist() {
        if (tenantId == null) {
            tenantId = TenantContext.requireTenant();
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

    public QuotaType getQuotaType() {
        return quotaType;
    }

    public void setQuotaType(QuotaType quotaType) {
        this.quotaType = quotaType;
    }

    public int getUsed() {
        return used;
    }

    public void setUsed(int used) {
        this.used = used;
    }

    public boolean isAlert80Sent() {
        return alert80Sent;
    }

    public void setAlert80Sent(boolean alert80Sent) {
        this.alert80Sent = alert80Sent;
    }

    public boolean isAlert100Sent() {
        return alert100Sent;
    }

    public void setAlert100Sent(boolean alert100Sent) {
        this.alert100Sent = alert100Sent;
    }

    public Instant getPeriodStart() {
        return periodStart;
    }

    public void setPeriodStart(Instant periodStart) {
        this.periodStart = periodStart;
    }

    public Instant getPeriodEnd() {
        return periodEnd;
    }

    public void setPeriodEnd(Instant periodEnd) {
        this.periodEnd = periodEnd;
    }
}
