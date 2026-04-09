package com.leadflow.backend.entities.lead;

import com.leadflow.backend.entities.enums.LeadStatus;
import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.multitenancy.context.TenantContext;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.ParamDef;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(
        name = "lead_status_history",
        schema = "public",
        indexes = {
                @Index(name = "idx_lsh_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_lsh_lead_id", columnList = "lead_id"),
                @Index(name = "idx_lsh_lead_tenant", columnList = "lead_id, tenant_id"),
                @Index(name = "idx_lsh_lead_changed_at", columnList = "lead_id,changed_at")
        }
)
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class LeadStatusHistory {

    /* ======================================================
       ID
       ====================================================== */

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (tenantId == null) {
            tenantId = TenantContext.requireTenant();
        }
    }

    /* ======================================================
       RELATIONSHIPS
       ====================================================== */

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "lead_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_lsh_lead")
    )
    private Lead lead;

    /**
     * Usuário que alterou o status.
     * Pode ser null quando alteração é SYSTEM.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = true)
    @JoinColumn(
            name = "changed_by",
            foreignKey = @ForeignKey(name = "fk_lsh_user")
    )
    private User changedBy;

    /* ======================================================
       FIELDS
       ====================================================== */

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private LeadStatus status;

    @CreationTimestamp
    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    /* ======================================================
       CONSTRUCTORS
       ====================================================== */

    protected LeadStatusHistory() {
        // Required by JPA
    }

    public LeadStatusHistory(Lead lead, LeadStatus status, User changedBy) {

        if (lead == null) {
            throw new IllegalArgumentException("Lead cannot be null");
        }

        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }

        this.lead = lead;
        this.status = status;
        this.changedBy = changedBy; // Pode ser null (SYSTEM)
    }

    /* ======================================================
       GETTERS
       ====================================================== */

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public Lead getLead() {
        return lead;
    }

    public LeadStatus getStatus() {
        return status;
    }

    public LocalDateTime getChangedAt() {
        return changedAt;
    }

    public User getChangedBy() {
        return changedBy;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    /* ======================================================
       EQUALS & HASHCODE (Hibernate-safe)
       ====================================================== */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof LeadStatusHistory other)) return false;
        return id != null && Objects.equals(id, other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /* ======================================================
       TO STRING
       ====================================================== */

    @Override
    public String toString() {
        return "LeadStatusHistory{" +
                "id=" + id +
                ", status=" + status +
                ", changedAt=" + changedAt +
                '}';
    }
}