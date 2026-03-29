package com.leadflow.backend.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "subscriptions")
public class Subscription {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    // ⚠️ NÃO pode ser obrigatório (lazy creation)
    @Column(name = "stripe_customer_id")
    private String stripeCustomerId;

    @Column(name = "stripe_subscription_id", unique = true)
    private String stripeSubscriptionId;

    // ⚠️ obrigatório no domínio
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "plan_id", nullable = false)
    private Plan plan;

    @Column
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private SubscriptionStatus status;

    @Column(name = "started_at", nullable = false)
    private LocalDateTime startedAt;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @Column(name = "last_payment_date")
    private LocalDateTime lastPaymentDate;

    @Column(name = "cancelled_at")
    private LocalDateTime cancelledAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    public Subscription() {}

    /* ======================================================
       FACTORY (DOMÍNIO)
       ====================================================== */

    public static Subscription createTrial(UUID tenantId, Plan plan) {

        if (tenantId == null) {
            throw new IllegalArgumentException("tenantId is required");
        }

        if (plan == null) {
            throw new IllegalArgumentException("plan is required");
        }

        Subscription sub = new Subscription();

        sub.tenantId = tenantId;
        sub.plan = plan;
        sub.status = SubscriptionStatus.TRIALING;
        
        // stripe_customer_id remains NULL until Stripe integration completes
        // Will be populated during checkout/payment process

        sub.startedAt = LocalDateTime.now();
        sub.expiresAt = LocalDateTime.now().plusDays(7);

        return sub;
    }

    /* ======================================================
       LIFECYCLE
       ====================================================== */

    @PrePersist
    protected void onCreate() {

        if (this.startedAt == null) {
            this.startedAt = LocalDateTime.now();
        }

        if (this.expiresAt == null) {
            this.expiresAt = this.startedAt.plusDays(7);
        }

        if (this.status == null) {
            this.status = SubscriptionStatus.INCOMPLETE;
        }

        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    @PreUpdate
    protected void onUpdate() {
        // ⚠️ CRÍTICO: validar status em UPDATE também (não só em INSERT)
        // Se status for null, usar INCOMPLETE como fallback
        if (this.status == null) {
            this.status = SubscriptionStatus.INCOMPLETE;
        }
        this.updatedAt = LocalDateTime.now();
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

    public String getStripeCustomerId() {
        return stripeCustomerId;
    }

    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }

    public String getStripeSubscriptionId() {
        return stripeSubscriptionId;
    }

    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
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

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public SubscriptionStatus getStatus() {
        return status;
    }

    public void setStatus(SubscriptionStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        this.status = status;
    }

    public LocalDateTime getStartedAt() {
        return startedAt;
    }

    public void setStartedAt(LocalDateTime startedAt) {
        this.startedAt = startedAt;
    }

    public LocalDateTime getExpiresAt() {
        return expiresAt;
    }

    public void setExpiresAt(LocalDateTime expiresAt) {
        this.expiresAt = expiresAt;
    }

    public LocalDateTime getLastPaymentDate() {
        return lastPaymentDate;
    }

    public void setLastPaymentDate(LocalDateTime lastPaymentDate) {
        this.lastPaymentDate = lastPaymentDate;
    }

    public LocalDateTime getCancelledAt() {
        return cancelledAt;
    }

    public void setCancelledAt(LocalDateTime cancelledAt) {
        this.cancelledAt = cancelledAt;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    /* ======================================================
       ENUM
       ====================================================== */

    public enum SubscriptionStatus {
        TRIALING,
        ACTIVE,
        PAST_DUE,
        CANCELLED,
        INCOMPLETE,
        COMPLETED
    }
}