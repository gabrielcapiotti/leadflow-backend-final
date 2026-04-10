package com.leadflow.backend.entities.vendor;

import jakarta.persistence.*;
import org.hibernate.annotations.Filter;
import com.leadflow.backend.multitenancy.context.TenantContext;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendor_lead_conversations", schema = "public", indexes = {
    @Index(name = "idx_vendor_lead_conv_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_vendor_lead_conv_vendor_tenant", columnList = "vendor_lead_id, tenant_id"),
    @Index(name = "idx_vendor_lead_conv_lead_tenant", columnList = "lead_id, tenant_id")
})
@Filter(
    name = "tenantFilter",
    condition = "tenant_id = :tenantId"
)
public class VendorLeadConversation {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID vendorLeadId;

    @Column(nullable = false)
    private UUID leadId;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(length = 5000, nullable = false)
    private String content;

    @Column(length = 20)
    private String role;

    @Column(length = 50)
    private String sender;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    /* ======================================================
       LIFECYCLE
       ====================================================== */

    @PrePersist
    public void prePersist() {
        if (tenantId == null) {
            tenantId = TenantContext.requireTenant();
        }
        if (createdAt == null) {
            createdAt = Instant.now();
        }
    }

    /* ======================================================
       GETTERS
       ====================================================== */

    public UUID getId() {
        return id;
    }

    public UUID getVendorLeadId() {
        return vendorLeadId;
    }

    public UUID getLeadId() {
        return leadId;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public String getContent() {
        return content;
    }

    public String getRole() {
        return role;
    }

    public String getSender() {
        return sender;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    /* ======================================================
       SETTERS
       ====================================================== */

    public void setVendorLeadId(UUID vendorLeadId) {
        this.vendorLeadId = vendorLeadId;
    }

    public void setLeadId(UUID leadId) {
        this.leadId = leadId;
    }

    public void setContent(String content) {
        this.content = content;
    }

    public void setRole(String role) {
        this.role = role;
    }

    public void setSender(String sender) {
        this.sender = sender;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}