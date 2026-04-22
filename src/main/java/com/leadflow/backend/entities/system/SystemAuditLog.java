package com.leadflow.backend.entities.system;

import com.leadflow.backend.multitenancy.context.TenantContext;
import jakarta.persistence.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_audit_logs", indexes = {
        @Index(name = "idx_sys_audit_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_sys_audit_tenant_action", columnList = "tenant_id, action"),
        @Index(name = "idx_sys_audit_created_at", columnList = "created_at")
})
public class SystemAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private String action;

    @Column(nullable = false)
    private String entity;

    @Column(name = "entity_id")
    private String entityId;

    @Column(length = 2000)
    private String details;

    @Column(name = "performed_by")
    private String performedBy;

    @Column(nullable = false)
    private Instant createdAt;

    public SystemAuditLog() {}

    @PrePersist
    public void prePersist() {
        createdAt = Instant.now();
        if (tenantId == null) {
            this.tenantId = TenantContext.requireTenant();
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

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntity() {
        return entity;
    }

    public void setEntity(String entity) {
        this.entity = entity;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getDetails() {
        return details;
    }

    public void setDetails(String details) {
        this.details = details;
    }

    public String getPerformedBy() {
        return performedBy;
    }

    public void setPerformedBy(String performedBy) {
        this.performedBy = performedBy;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }
}
