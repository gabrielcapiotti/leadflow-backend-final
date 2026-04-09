package com.leadflow.backend.entities.audit;

import com.leadflow.backend.multitenancy.context.TenantContext;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "security_audit_logs",
        indexes = {
                @Index(name = "idx_sec_audit_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_sec_audit_email_tenant", columnList = "email, tenant_id"),
                @Index(name = "idx_sec_audit_created_at", columnList = "created_at")
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
       TENANT
       ====================================================== */

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /* ======================================================
       ACTION
       ====================================================== */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SecurityAction action;

    /* ======================================================
       CONTEXT
       ====================================================== */

    @Column(nullable = false, length = 150)
    private String email;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "ip_address", length = 100)
    private String ipAddress;

    @Column(name = "user_agent", length = 255)
    private String userAgent;

    @Column(name = "correlation_id", length = 100)
    private String correlationId;

    /* ======================================================
       AUDIT
       ====================================================== */

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /* ======================================================
       CONSTRUCTORS
       ====================================================== */

    protected SecurityAuditLog() {
        // Required by JPA
    }

    public SecurityAuditLog(
            SecurityAction action,
            String email,
            UUID tenantId,
            boolean success,
            String ipAddress,
            String userAgent,
            String correlationId
    ) {
        this.action = Objects.requireNonNull(action);
        this.email = normalize(email);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.success = success;
        this.ipAddress = ipAddress;
        this.userAgent = userAgent;
        this.correlationId = correlationId;
    }

    @PrePersist
    public void prePersist() {
        if (tenantId == null) {
            this.tenantId = TenantContext.requireTenant();
        }
    }

    /* ======================================================
       NORMALIZATION
       ====================================================== */

    private String normalize(String value) {
        return value == null ? null : value.trim().toLowerCase();
    }

    /* ======================================================
       GETTERS
       ====================================================== */

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public SecurityAction getAction() { return action; }
    public String getEmail() { return email; }
    public boolean isSuccess() { return success; }
    public String getIpAddress() { return ipAddress; }
    public String getUserAgent() { return userAgent; }
    public String getCorrelationId() { return correlationId; }
    public LocalDateTime getCreatedAt() { return createdAt; }

    /* ======================================================
       SETTERS
       ====================================================== */

    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setAction(SecurityAction action) { this.action = action; }
    public void setEmail(String email) { this.email = normalize(email); }
    public void setSuccess(boolean success) { this.success = success; }
    public void setIpAddress(String ipAddress) { this.ipAddress = ipAddress; }
    public void setUserAgent(String userAgent) { this.userAgent = userAgent; }
    public void setCorrelationId(String correlationId) { this.correlationId = correlationId; }

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
                ", tenantId=" + tenantId +
                ", action=" + action +
                ", email='" + email + '\'' +
                ", success=" + success +
                ", createdAt=" + createdAt +
                '}';
    }
}