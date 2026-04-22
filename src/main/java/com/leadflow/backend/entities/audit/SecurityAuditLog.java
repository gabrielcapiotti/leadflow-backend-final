package com.leadflow.backend.entities.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.leadflow.backend.multitenancy.context.TenantContext;
import jakarta.persistence.*;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "audit_logs",
        schema = "public",
        indexes = {
                @Index(name = "idx_audit_logs_category", columnList = "event_category"),
                @Index(name = "idx_audit_logs_action", columnList = "action"),
                @Index(name = "idx_audit_logs_actor_email", columnList = "actor_email"),
                @Index(name = "idx_audit_logs_tenant", columnList = "tenant_id"),
                @Index(name = "idx_audit_logs_entity_type", columnList = "entity_type"),
                @Index(name = "idx_audit_logs_entity_id", columnList = "entity_id"),
                @Index(name = "idx_audit_logs_created_at", columnList = "created_at DESC"),
                @Index(name = "idx_audit_logs_correlation", columnList = "correlation_id")
        }
)
public class SecurityAuditLog {

    /* ======================================================
       ID
       ====================================================== */

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /* ======================================================
       CLASSIFICAÇÃO DO EVENTO
       ====================================================== */

    @Column(name = "event_category", nullable = false, length = 50)
    private String eventCategory = "SECURITY";

    @Column(nullable = false, length = 100)
    private String action;

    /* ======================================================
       CONTEXTO DO ATOR
       ====================================================== */

    @Column(name = "actor_email", length = 255)
    private String actorEmail;

    @Column(name = "tenant_id")
    private UUID tenantId;

    /* ======================================================
       CONTEXTO DA ENTIDADE
       ====================================================== */

    @Column(name = "entity_type", length = 100)
    private String entityType = "SECURITY";

    @Column(name = "entity_id")
    private UUID entityId;

    /* ======================================================
       SEGURANÇA
       ====================================================== */

    @Column
    private Boolean success;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    /* ======================================================
       RASTREABILIDADE
       ====================================================== */

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    /* ======================================================
       PAYLOAD / DETALHES
       ====================================================== */

    @Column(columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private JsonNode details;

    /* ======================================================
       AUDITORIA
       ====================================================== */

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    /* ======================================================
       CONSTRUCTORS
       ====================================================== */

    protected SecurityAuditLog() {
        // Required by JPA
    }

    public SecurityAuditLog(
            String action,
            String actorEmail,
            UUID tenantId,
            String entityType,
            UUID entityId,
            Boolean success,
            String ipAddress,
            String userAgent,
            String correlationId,
            JsonNode details
    ) {
        this.eventCategory = "SECURITY";
        this.action = Objects.requireNonNull(action);
        this.actorEmail = actorEmail;
        this.tenantId = tenantId;
        this.entityType = entityType;
        this.entityId = entityId;
        this.success = success;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.correlationId = correlationId;
        this.details = details;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (tenantId == null) {
            try {
                this.tenantId = TenantContext.requireTenant();
            } catch (Exception e) {
                // Tenant context may not be available in all contexts
            }
        }
    }

    /* ======================================================
       GETTERS
       ====================================================== */

    public UUID getId() { return id; }
    public String getEventCategory() { return eventCategory; }
    public String getAction() { return action; }
    public String getActorEmail() { return actorEmail; }
    public UUID getTenantId() { return tenantId; }
    public String getEntityType() { return entityType; }
    public UUID getEntityId() { return entityId; }
    public Boolean getSuccess() { return success; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getCorrelationId() { return correlationId; }
    public JsonNode getDetails() { return details; }
    public Instant getCreatedAt() { return createdAt; }

    /* ======================================================
       SETTERS
       ====================================================== */

    public void setEventCategory(String eventCategory) { this.eventCategory = eventCategory; }
    public void setAction(String action) { this.action = action; }
    public void setActorEmail(String actorEmail) { this.actorEmail = actorEmail; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setEntityType(String entityType) { this.entityType = entityType; }
    public void setEntityId(UUID entityId) { this.entityId = entityId; }
    public void setSuccess(Boolean success) { this.success = success; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }
    public void setDetails(JsonNode details) { this.details = details; }

    /* ======================================================
       EQUALS & HASHCODE
       ====================================================== */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SecurityAuditLog other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /* ======================================================
       TO STRING (Safe)
       ====================================================== */

    @Override
    public String toString() {
        return "SecurityAuditLog{" +
                "id=" + id +
                ", eventCategory='" + eventCategory + '\'' +
                ", action='" + action + '\'' +
                ", actorEmail='" + actorEmail + '\'' +
                ", tenantId=" + tenantId +
                ", success=" + success +
                ", createdAt=" + createdAt +
                '}';
    }
}