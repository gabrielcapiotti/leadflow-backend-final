package com.leadflow.backend.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Controla consumo total de recursos do tenant.
 * Sempre deve existir 1 registro por tenant.
 */
@Entity
@Table(name = "usage_limits", schema = "public")
public class UsageLimit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, unique = true)
    private UUID tenantId;

    @Column(name = "leads_used", nullable = false)
    private Integer leadsUsed;

    @Column(name = "users_used", nullable = false)
    private Integer usersUsed;

    @Column(name = "ai_executions_used", nullable = false)
    private Integer aiExecutionsUsed;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public UsageLimit() {}

    /* ======================================================
       FACTORY (PADRÃO DO DOMÍNIO)
       ====================================================== */

    public static UsageLimit create(UUID tenantId, Plan plan) {

        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }

        if (plan == null) {
            throw new IllegalArgumentException("plan is required");
        }

        UsageLimit usage = new UsageLimit();

        usage.tenantId = tenantId;
        usage.plan = plan;

        usage.leadsUsed = 0;
        usage.usersUsed = 0;
        usage.aiExecutionsUsed = 0;

        return usage;
    }

    /* ======================================================
       LIFECYCLE
       ====================================================== */

    @PrePersist
    protected void onCreate() {

        if (leadsUsed == null) leadsUsed = 0;
        if (usersUsed == null) usersUsed = 0;
        if (aiExecutionsUsed == null) aiExecutionsUsed = 0;

        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    /* ======================================================
       DOMAIN METHODS (SAFE INCREMENTS)
       ====================================================== */

    public void incrementLeads(int amount) {
        validatePositive(amount);
        this.leadsUsed += amount;
    }

    public void incrementUsers(int amount) {
        validatePositive(amount);
        this.usersUsed += amount;
    }

    public void incrementAiExecutions(int amount) {
        validatePositive(amount);
        this.aiExecutionsUsed += amount;
    }

    private void validatePositive(int amount) {
        if (amount < 0) {
            throw new IllegalArgumentException("Amount must be >= 0");
        }
    }

    /* ======================================================
       GETTERS / SETTERS
       ====================================================== */

    public Long getId() {
        return id;
    }

    public UUID getTenantId() {
        return tenantId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public Integer getLeadsUsed() {
        return leadsUsed != null ? leadsUsed : 0;
    }

    public void setLeadsUsed(Integer leadsUsed) {
        this.leadsUsed = (leadsUsed != null && leadsUsed >= 0) ? leadsUsed : 0;
    }

    public Integer getUsersUsed() {
        return usersUsed != null ? usersUsed : 0;
    }

    public void setUsersUsed(Integer usersUsed) {
        this.usersUsed = (usersUsed != null && usersUsed >= 0) ? usersUsed : 0;
    }

    public Integer getAiExecutionsUsed() {
        return aiExecutionsUsed != null ? aiExecutionsUsed : 0;
    }

    public void setAiExecutionsUsed(Integer aiExecutionsUsed) {
        this.aiExecutionsUsed = (aiExecutionsUsed != null && aiExecutionsUsed >= 0)
                ? aiExecutionsUsed
                : 0;
    }

    public Plan getPlan() {
        return plan;
    }

    public void setPlan(Plan plan) {
        if (plan == null) {
            throw new IllegalArgumentException("Plan cannot be null");
        }
        this.plan = plan;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}