package com.leadflow.backend.entities.vendor;

import jakarta.persistence.*;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.ParamDef;
import com.leadflow.backend.multitenancy.context.TenantContext;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendor_lead_stage_history", schema = "public", indexes = {
    @Index(name = "idx_vendor_lead_stage_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_vendor_lead_stage_vendor_lead_tenant", columnList = "vendor_lead_id, tenant_id")
})
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class VendorLeadStageHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID vendorLeadId;

    @Column(nullable = false)
    private String previousStage;

    @Column(nullable = false)
    private String newStage;

    @Column(nullable = false, updatable = false)
    private Instant changedAt;

    public VendorLeadStageHistory() {}

    @PrePersist
    public void onCreate() {
        if (tenantId == null) {
            tenantId = TenantContext.requireTenant();
        }
        this.changedAt = Instant.now();
    }

    // GETTERS

    public UUID getId() { return id; }

    public UUID getTenantId() { return tenantId; }

    public UUID getVendorLeadId() { return vendorLeadId; }
    public String getPreviousStage() { return previousStage; }
    public String getNewStage() { return newStage; }
    public Instant getChangedAt() { return changedAt; }

    // SETTERS

    public void setVendorLeadId(UUID vendorLeadId) {
        this.vendorLeadId = vendorLeadId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public void setPreviousStage(String previousStage) {
        this.previousStage = previousStage;
    }

    public void setNewStage(String newStage) {
        this.newStage = newStage;
    }
}