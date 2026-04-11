package com.leadflow.backend.entities.vendor;

import com.leadflow.backend.multitenancy.context.TenantContext;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "vendor_leads")
public class VendorLead {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(nullable = false)
    private UUID vendorId;

    @Column(nullable = false)
    private String nomeCompleto;

    @Column(nullable = false)
    private String whatsapp;

    private String tipoConsorcio;
    private BigDecimal valorCredito;
    private String urgencia;

    // 🔵 NOVO CAMPO (FASE 1 - CRM)
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LeadStage stage = LeadStage.NEW;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdDate;

    @Column(nullable = false)
    private Integer score = 0;

    @Column
    private String ownerEmail;

    @Column(columnDefinition = "TEXT")
    private String resumoEstrategico;

    public VendorLead() {}

    @PrePersist
    public void onCreate() {
        this.createdDate = Instant.now();
        if (tenantId == null) {
            this.tenantId = TenantContext.requireTenant();
        }
    }

    // ======================
    // GETTERS
    // ======================

    public UUID getId() { return id; }
    public UUID getTenantId() { return tenantId; }
    public UUID getVendorId() { return vendorId; }
    public String getNomeCompleto() { return nomeCompleto; }
    public String getWhatsapp() { return whatsapp; }
    public String getTipoConsorcio() { return tipoConsorcio; }
    public BigDecimal getValorCredito() { return valorCredito; }
    public String getUrgencia() { return urgencia; }
    public LeadStage getStage() { return stage; }
    public Instant getCreatedDate() { return createdDate; }
    public Integer getScore() { return score; }
    public String getOwnerEmail() { return ownerEmail; }
    public String getResumoEstrategico() { return resumoEstrategico; }

    // ======================
    // SETTERS
    // ======================

    public void setTenantId(UUID tenantId) { this.tenantId = tenantId; }
    public void setVendorId(UUID vendorId) { this.vendorId = vendorId; }
    public void setNomeCompleto(String nomeCompleto) { this.nomeCompleto = nomeCompleto; }
    public void setWhatsapp(String whatsapp) { this.whatsapp = whatsapp; }
    public void setTipoConsorcio(String tipoConsorcio) { this.tipoConsorcio = tipoConsorcio; }
    public void setValorCredito(BigDecimal valorCredito) { this.valorCredito = valorCredito; }
    public void setUrgencia(String urgencia) { this.urgencia = urgencia; }
    public void setStage(LeadStage stage) { this.stage = stage; }
    public void setScore(Integer score) { this.score = score; }
    public void setOwnerEmail(String ownerEmail) { this.ownerEmail = ownerEmail; }
    public void setResumoEstrategico(String resumoEstrategico) {
        this.resumoEstrategico = resumoEstrategico;
    }
}