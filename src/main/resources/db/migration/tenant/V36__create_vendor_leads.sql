/* ======================================================
   V36__create_vendor_leads.sql (FINAL - DETERMINISTIC)
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
        CHECK (stage IN ('NEW','CONTACT','PROPOSAL','NEGOTIATION','CLOSED','LOST')),

    status VARCHAR(50) NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW','CONTACT','PROPOSAL','NEGOTIATION','CLOSED','LOST')),

    /* ========== SCORING ========== */

    score INT NOT NULL DEFAULT 0
        CHECK (score BETWEEN 0 AND 100),

    /* ========== CRM ========== */

    owner_email VARCHAR(255),
    resumo_estrategico TEXT,

    /* ========== AUDITORIA ========== */

    created_date TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
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
   INDEXES
   ====================================================== */

CREATE INDEX idx_vendor_leads_tenant_id
    ON vendor_leads (tenant_id);

CREATE INDEX idx_vendor_leads_vendor
    ON vendor_leads (vendor_id);

CREATE INDEX idx_vendor_leads_tenant_vendor
    ON vendor_leads (tenant_id, vendor_id);

CREATE INDEX idx_vendor_leads_stage
    ON vendor_leads (stage);

CREATE INDEX idx_vendor_leads_vendor_stage
    ON vendor_leads (vendor_id, stage);

CREATE INDEX idx_vendor_leads_created_date
    ON vendor_leads (created_date DESC);