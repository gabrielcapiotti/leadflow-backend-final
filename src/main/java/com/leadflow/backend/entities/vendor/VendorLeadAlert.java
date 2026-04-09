package com.leadflow.backend.entities.vendor;

import jakarta.persistence.*;
import org.hibernate.annotations.FilterDef;
import org.hibernate.annotations.Filter;
import org.hibernate.annotations.ParamDef;
import com.leadflow.backend.multitenancy.context.TenantContext;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendor_lead_alerts", indexes = {
    @Index(name = "idx_vendor_lead_alert_tenant_id", columnList = "tenant_id"),
    @Index(name = "idx_vendor_lead_alert_vendor_tenant", columnList = "vendor_lead_id, tenant_id")
})
@FilterDef(name = "tenantFilter", parameters = @ParamDef(name = "tenantId", type = UUID.class))
@Filter(name = "tenantFilter", condition = "tenant_id = :tenantId")
public class VendorLeadAlert {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID vendorLeadId;

    @Column(nullable = false)
    private String tipo;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String mensagem;

    @Column(nullable = false)
    private boolean resolvido = false;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    public VendorLeadAlert() {}

    @PrePersist
    public void onCreate() {
        if (tenantId == null) {
            tenantId = TenantContext.requireTenant();
        }
        this.createdAt = Instant.now();
    }

    public UUID getId() { return id; }

    public UUID getTenantId() { return tenantId; }

    public UUID getVendorLeadId() { return vendorLeadId; }
    public String getTipo() { return tipo; }
    public String getMensagem() { return mensagem; }
    public boolean isResolvido() { return resolvido; }
    public Instant getCreatedAt() { return createdAt; }

    public void setVendorLeadId(UUID vendorLeadId) {
        this.vendorLeadId = vendorLeadId;
    }

    public void setTenantId(UUID tenantId) {
        this.tenantId = tenantId;
    }

    public void setTipo(String tipo) {
        this.tipo = tipo;
    }

    public void setMensagem(String mensagem) {
        this.mensagem = mensagem;
    }

    public void setResolvido(boolean resolvido) {
        this.resolvido = resolvido;
    }
}
