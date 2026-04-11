package com.leadflow.backend.entities;

import jakarta.persistence.*;
import lombok.*;
import java.time.Instant;
import java.util.UUID;
import com.fasterxml.jackson.annotation.JsonIgnore;
import org.hibernate.type.SqlTypes;
import org.hibernate.annotations.JdbcTypeCode;

/**
 * CRITICAL: Maps Stripe customer_id to local tenant_id
 * SOURCE OF TRUTH for webhook processing
 * 
 * Enables stateless, reliable webhook handling without API calls to Stripe
 * at webhook time.
 * 
 * @author Webhook Mapping Service
 */
@Entity
@Table(
    name = "stripe_customers",
    schema = "public",
    indexes = {
        @Index(name = "idx_stripe_customers_stripe_customer_id", columnList = "stripe_customer_id"),
        @Index(name = "idx_stripe_customers_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_stripe_customers_subscription_id", columnList = "subscription_id"),
        @Index(name = "idx_stripe_customers_tenant_stripe_unique", 
               columnList = "tenant_id,stripe_customer_id", unique = true)
    },
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_stripe_customer_id", columnNames = "stripe_customer_id")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"tenant", "subscription"})
public class StripeCustomer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /**
     * Tenant this mapping belongs to (security boundary)
     * REQUIRED: Cannot be null
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
        name = "tenant_id",
        nullable = false,
        referencedColumnName = "id",
        foreignKey = @ForeignKey(name = "fk_stripe_customers_tenant_id")
    )
    @JsonIgnore
    private Tenant tenant;

    /**
     * Stripe API customer ID (e.g., cus_123456789)
     * REQUIRED: Unique key for webhook lookups
     */
    @Column(
        name = "stripe_customer_id",
        nullable = false,
        length = 255,
        unique = true
    )
    private String stripeCustomerId;

    /**
     * Optional: Reference to local subscription
     * Helpful for subscription-specific webhooks
     */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(
        name = "subscription_id",
        referencedColumnName = "id",
        foreignKey = @ForeignKey(name = "fk_stripe_customers_subscription_id")
    )
    @JsonIgnore
    private Subscription subscription;

    /**
     * Lifecycle status
     * active = normal operation
     * inactive = disabled but kept for history
     * deleted = cleanup marker
     */
    @Column(name = "status", nullable = false, length = 50)
    @Builder.Default
    private String status = "active";

    /**
     * When mapping was created
     */
    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private Instant createdAt = Instant.now();

    /**
     * When mapping was last updated
     */
    @Column(name = "updated_at", nullable = false)
    @Builder.Default
    private Instant updatedAt = Instant.now();

    /**
     * Timestamp of last webhook processed
     * Useful for monitoring/debugging
     */
    @Column(name = "last_webhook_at")
    private Instant lastWebhookAt;

    /**
     * Flexible metadata storage
     * For future extensibility
     */
    @Column(name = "metadata", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    private String metadata;

    // Lifecycle hooks
    @PrePersist
    protected void onCreate() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.updatedAt == null) {
            this.updatedAt = Instant.now();
        }
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }

    /**
     * Mark that a webhook was processed for this customer
     */
    public void recordWebhookProcessed() {
        this.lastWebhookAt = Instant.now();
        this.updatedAt = Instant.now();
    }
}
