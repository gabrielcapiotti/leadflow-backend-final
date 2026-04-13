package com.leadflow.backend.entities.payment;

import com.leadflow.backend.multitenancy.context.TenantContext;
import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "payments", indexes = {
        @Index(name = "idx_payment_event", columnList = "eventId", unique = true),
        @Index(name = "idx_payment_tenant_id", columnList = "tenant_id"),
        @Index(name = "idx_payment_email_tenant", columnList = "email, tenant_id"),
        @Index(name = "idx_payment_status_date", columnList = "status, createdAt")
})
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false, unique = true)
    private String eventId;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String status; // pending | paid | failed

    @Column(nullable = false)
    private String gateway; // stripe | mercado_pago

    @Column(precision = 10, scale = 2)
    private BigDecimal amount; // Valor real do Stripe em USD

    @Column(columnDefinition = "TEXT")
    private String payload;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public Payment() {}

    @PrePersist
    public void onCreate() {
        this.createdAt = Instant.now();
        if (tenantId == null) {
            this.tenantId = TenantContext.requireTenant();
        }
    }

    // getters e setters
    public UUID getId() { return id; }

    public UUID getTenantId() { return tenantId; }
    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }

    public String getEventId() { return eventId; }
    public void setEventId(String eventId) { this.eventId = eventId; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getGateway() { return gateway; }
    public void setGateway(String gateway) { this.gateway = gateway; }

    public String getPayload() { return payload; }
    public void setPayload(String payload) { this.payload = payload; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Instant getCreatedAt() { return createdAt; }
}