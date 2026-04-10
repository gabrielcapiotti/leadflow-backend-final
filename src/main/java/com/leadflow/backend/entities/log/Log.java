package com.leadflow.backend.entities.log;

import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.multitenancy.context.TenantContext;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "logs",
    schema = "public",
    indexes = {
        @Index(name = "idx_logs_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_logs_user_id", columnList = "user_id"),
        @Index(name = "idx_logs_created_at", columnList = "created_at"),
        @Index(name = "idx_logs_tenant_created", columnList = "tenant_id, created_at")
    }
)
@Filter(
    name = "tenantFilter",
    condition = "tenant_id = :tenantId"
)
public class Log {

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

    /*
     * Usuário que realizou a ação (pode ser null – ex: sistema)
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "user_id",
        foreignKey = @ForeignKey(name = "fk_logs_user")
    )
    private User user;

    /*
     * Descrição da ação executada
     */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String action;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    /* ==========================
       CONSTRUTORES
       ========================== */

    protected Log() {
        // JPA only
    }

    public Log(User user, String action) {

        if (action == null || action.isBlank()) {
            throw new IllegalArgumentException("Action cannot be null or blank");
        }

        this.user = user;
        this.action = action.trim();
    }

    /* ==========================
       GETTERS (imutável)
       ========================== */

    public UUID getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public User getUser() {
        return user;
    }

    public String getAction() {
        return action;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    /* ==========================
       EQUALS & HASHCODE (JPA SAFE)
       ========================== */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Log other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }

    /* ==========================
       TO STRING
       ========================== */

    @Override
    public String toString() {
        return "Log{" +
               "id=" + id +
               ", action='" + action + '\'' +
               ", createdAt=" + createdAt +
               '}';
    }
}
