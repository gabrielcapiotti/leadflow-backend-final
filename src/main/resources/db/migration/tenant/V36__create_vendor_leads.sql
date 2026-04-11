/* ======================================================
   V36__create_vendor_leads.sql (FINAL - CORRECTED & DETERMINISTIC)
   ====================================================== */

CREATE TABLE vendor_leads (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    /* ========== MULTI-TENANT ========== */

    tenant_id UUID NOT NULL,
    vendor_id UUID NOT NULL,

    /* ========== LEAD DATA ========== */

    nome_completo VARCHAR(200) NOT NULL,
    whatsapp VARCHAR(30) NOT NULL,

    tipo_consorcio VARCHAR(100),
    valor_credito NUMERIC(15,2),

    urgencia VARCHAR(20),

    /* ========== PIPELINE ========== */

    stage VARCHAR(50) NOT NULL DEFAULT 'NEW'
        CHECK (stage IN ('NEW','CONTACT','NEGOTIATION','CLOSED','LOST')),

    /* ========== SCORING ========== */

    score INT NOT NULL DEFAULT 0
        CHECK (score BETWEEN 0 AND 100),

    /* ========== CRM ========== */

    owner_email VARCHAR(255),
    resumo_estrategico TEXT,

    /* ========== AUDITORIA (PADRONIZADO) ========== */

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    /* ========== CONSTRAINTS ========== */

    CONSTRAINT fk_vendor_leads_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES vendors(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_vendor_leads_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenants(id)
        ON DELETE CASCADE
);

/* ======================================================
   INDEXES (DETERMINÍSTICOS)
   ====================================================== */

CREATE INDEX idx_vendor_leads_tenant_id
    ON vendor_leads (tenant_id);

CREATE INDEX idx_vendor_leads_vendor_id
    ON vendor_leads (vendor_id);

CREATE INDEX idx_vendor_leads_tenant_vendor
    ON vendor_leads (tenant_id, vendor_id);

CREATE INDEX idx_vendor_leads_stage
    ON vendor_leads (stage);

CREATE INDEX idx_vendor_leads_vendor_stage
    ON vendor_leads (vendor_id, stage);

CREATE INDEX idx_vendor_leads_created_at
    ON vendor_leads (created_at DESC);

/* 🔥 ESSENCIAL PARA SAAS (QUERY POR TENANT + TIME) */
CREATE INDEX idx_vendor_leads_tenant_created
    ON vendor_leads (tenant_id, created_at DESC);

/* ======================================================
   VENDOR AUDIT LOGS
   ====================================================== */

CREATE TABLE vendor_audit_logs (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    /* ========== MULTI-TENANT ========== */

    tenant_id UUID NOT NULL,
    vendor_id UUID NOT NULL,

    /* ========== AUDIT DATA ========== */

    user_email VARCHAR(255) NOT NULL,
    acao VARCHAR(255) NOT NULL,

    entity_type VARCHAR(255) NOT NULL,
    entidade_id UUID NOT NULL,

    detalhes TEXT,

    /* ========== TIMESTAMPS ========== */

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    /* ========== CONSTRAINTS ========== */

    CONSTRAINT fk_vendor_audit_logs_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES vendors(id)
        ON DELETE CASCADE,

    /* 🔥 CRÍTICO: GARANTE ISOLAMENTO */
    CONSTRAINT fk_vendor_audit_logs_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES tenants(id)
        ON DELETE CASCADE
);

/* ======================================================
   INDEXES (AUDIT LOGS)
   ====================================================== */

CREATE INDEX idx_vendor_audit_logs_tenant
    ON vendor_audit_logs(tenant_id);

CREATE INDEX idx_vendor_audit_logs_vendor
    ON vendor_audit_logs(vendor_id);

CREATE INDEX idx_vendor_audit_logs_tenant_vendor
    ON vendor_audit_logs(tenant_id, vendor_id);

CREATE INDEX idx_vendor_audit_logs_vendor_created
    ON vendor_audit_logs(vendor_id, created_at DESC);

/* 🔥 ESSENCIAL PARA CONSULTAS POR TENANT */
CREATE INDEX idx_vendor_audit_logs_tenant_created
    ON vendor_audit_logs(tenant_id, created_at DESC);

CREATE INDEX idx_vendor_audit_logs_entity
    ON vendor_audit_logs(entity_type, entidade_id);

CREATE INDEX idx_vendor_audit_logs_action_created
    ON vendor_audit_logs(acao, created_at DESC);