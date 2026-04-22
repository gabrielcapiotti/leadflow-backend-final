package com.leadflow.backend.entities.vendor;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import com.leadflow.backend.multitenancy.context.TenantContext;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "vendor_lead_messages",
        schema = "public",
        indexes = {
                @Index(name = "idx_vlm_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_vlm_vendor_lead_id", columnList = "vendor_lead_id"),
                @Index(name = "idx_vlm_vendor_lead_tenant", columnList = "vendor_lead_id, tenant_id"),
                @Index(name = "idx_vlm_vendor_lead_created_at", columnList = "vendor_lead_id,created_at")
        }
)
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class VendorLeadMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID vendorLeadId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ConversationRole role;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String message;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public VendorLeadMessage() {
    }

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

    public UUID getVendorLeadId() {
        return vendorLeadId;
    }

    public ConversationRole getRole() {
        return role;
    }

    public String getMessage() {
        return message;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setVendorLeadId(UUID vendorLeadId) {
        this.vendorLeadId = vendorLeadId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public void setRole(ConversationRole role) {
        this.role = role;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}