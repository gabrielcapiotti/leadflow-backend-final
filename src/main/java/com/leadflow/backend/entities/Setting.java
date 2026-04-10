package com.leadflow.backend.entities;

import com.leadflow.backend.entities.user.User;
import com.leadflow.backend.multitenancy.context.TenantContext;
import jakarta.persistence.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.Filter;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(
    name = "settings",
    schema = "public",
    indexes = {
        @Index(name = "idx_settings_user", columnList = "user_id"),
        @Index(name = "idx_settings_tenant", columnList = "tenant_id")
    }
)
@Filter(
    name = "tenantFilter",
    condition = "tenant_id = :tenantId"
)
public class Setting {

    /* ==========================
       ID
       ========================== */

    @Id
    @Column(nullable = false, updatable = false)
    private UUID id;

    @PrePersist
    public void prePersist() {
        if (id == null) {
            id = UUID.randomUUID();
        }
        if (tenantId == null) {
            this.tenantId = TenantContext.requireTenant();
        }
    }

    /* ==========================
       MULTI-TENANT
       ========================== */

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    /* ==========================
       RELACIONAMENTO
       ========================== */

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(
            name = "user_id",
            nullable = false,
            foreignKey = @ForeignKey(name = "fk_settings_user")
    )
    private User user;

    /* ==========================
       CAMPOS
       ========================== */

    @Column(name = "vendor_name", nullable = false, length = 100)
    private String vendorName;

    @Column(nullable = false, length = 15)
    private String whatsapp;

    @Column(name = "company_name", length = 100)
    private String companyName;

    @Column(columnDefinition = "TEXT")
    private String logo;

    @Column(name = "welcome_message", columnDefinition = "TEXT")
    private String welcomeMessage;

    /* ==========================
       AUDITORIA
       ========================== */

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /* ==========================
       CONSTRUTORES
       ========================== */

    protected Setting() {
        // JPA
    }

    public Setting(
            User user,
            String vendorName,
            String whatsapp,
            String companyName,
            String logo,
            String welcomeMessage
    ) {
        if (user == null) {
            throw new IllegalArgumentException("User cannot be null");
        }

        validateVendorName(vendorName);
        validateWhatsapp(whatsapp);

        this.user = user;
        this.vendorName = vendorName.trim();
        this.whatsapp = whatsapp.trim();
        this.companyName = normalize(companyName);
        this.logo = normalize(logo);
        this.welcomeMessage = normalize(welcomeMessage);
    }

    /* ==========================
       GETTERS
       ========================== */

    public UUID getId() { return id; }

    public UUID getTenantId() { return tenantId; }

    public User getUser() { return user; }

    public String getVendorName() { return vendorName; }

    public String getWhatsapp() { return whatsapp; }

    public String getCompanyName() { return companyName; }

    public String getLogo() { return logo; }

    public String getWelcomeMessage() { return welcomeMessage; }

    public LocalDateTime getCreatedAt() { return createdAt; }

    public LocalDateTime getUpdatedAt() { return updatedAt; }

    public LocalDateTime getDeletedAt() { return deletedAt; }

    /* ==========================
       SETTERS (for partial updates)
       ========================== */

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public void setVendorName(String vendorName) {
        validateVendorName(vendorName);
        this.vendorName = vendorName.trim();
    }

    public void setWhatsapp(String whatsapp) {
        validateWhatsapp(whatsapp);
        this.whatsapp = whatsapp.trim();
    }

    public void setCompanyName(String companyName) {
        this.companyName = normalize(companyName);
    }

    public void setLogo(String logo) {
        this.logo = normalize(logo);
    }

    public void setWelcomeMessage(String welcomeMessage) {
        this.welcomeMessage = normalize(welcomeMessage);
    }

    /* ==========================
       REGRAS DE DOMÍNIO
       ========================== */

    public void update(
            String vendorName,
            String whatsapp,
            String companyName,
            String logo,
            String welcomeMessage
    ) {
        ensureNotDeleted();

        validateVendorName(vendorName);
        validateWhatsapp(whatsapp);

        this.vendorName = vendorName.trim();
        this.whatsapp = whatsapp.trim();
        this.companyName = normalize(companyName);
        this.logo = normalize(logo);
        this.welcomeMessage = normalize(welcomeMessage);
    }

    public void softDelete() {
        ensureNotDeleted();
        this.deletedAt = LocalDateTime.now();
    }

    public void restore() {
        this.deletedAt = null;
    }

    public boolean isDeleted() {
        return deletedAt != null;
    }

    private void ensureNotDeleted() {
        if (isDeleted()) {
            throw new IllegalStateException("Cannot modify deleted setting");
        }
    }

    private void validateVendorName(String vendorName) {
        if (vendorName == null || vendorName.isBlank()) {
            throw new IllegalArgumentException("Vendor name cannot be blank");
        }
    }

    private void validateWhatsapp(String whatsapp) {
        if (whatsapp == null || whatsapp.isBlank()) {
            throw new IllegalArgumentException("Whatsapp cannot be blank");
        }
    }

    private String normalize(String value) {
        return value == null ? null : value.trim();
    }

    /* ==========================
       EQUALS & HASHCODE
       ========================== */

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Setting other)) return false;
        return id != null && id.equals(other.id);
    }

    @Override
    public int hashCode() {
        return getClass().hashCode();
    }
}
