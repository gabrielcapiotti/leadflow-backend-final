package com.leadflow.backend.entities;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "plans")
public class Plan {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true, length = 50)
    private String code;

    @Column(nullable = false, unique = true, length = 100)
    private String name;

    @Column(name = "max_leads", nullable = false)
    private Integer maxLeads;

    @Column(name = "max_users", nullable = false)
    private Integer maxUsers;

    @Column(name = "max_ai_executions", nullable = false)
    private Integer maxAiExecutions;

    @Column(nullable = false)
    private Boolean active;

    @Column(name = "stripe_product_id", length = 255)
    private String stripeProductId;

    @Column(name = "stripe_price_id", length = 255)
    private String stripePriceId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    public Plan() {
    }

    @PrePersist
    protected void onCreate() {
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    // Getters and Setters

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Integer getMaxLeads() {
        return maxLeads;
    }

    public void setMaxLeads(Integer maxLeads) {
        this.maxLeads = maxLeads;
    }

    public Integer getMaxUsers() {
        return maxUsers;
    }

    public void setMaxUsers(Integer maxUsers) {
        this.maxUsers = maxUsers;
    }

    public Integer getMaxAiExecutions() {
        return maxAiExecutions;
    }

    public void setMaxAiExecutions(Integer maxAiExecutions) {
        this.maxAiExecutions = maxAiExecutions;
    }

    public Boolean getActive() {
        return active;
    }

    public void setActive(Boolean active) {
        this.active = active;
    }

    public String getStripeProductId() {
        return stripeProductId;
    }

    public void setStripeProductId(String stripeProductId) {
        this.stripeProductId = stripeProductId;
    }

    public String getStripePriceId() {
        return stripePriceId;
    }

    public void setStripePriceId(String stripePriceId) {
        this.stripePriceId = stripePriceId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
}
