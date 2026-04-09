/* ======================================================
   V36__create_vendor_leads.sql
   TENANT SCHEMA
   ====================================================== */

CREATE TABLE vendor_leads (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    /* ========== MULTI-TENANT (COLUMN-BASED) ========== */

    tenant_id UUID NOT NULL,

    vendor_id UUID NOT NULL,

    nome_completo VARCHAR(200) NOT NULL,
    whatsapp VARCHAR(30) NOT NULL,

    tipo_consorcio VARCHAR(100),
    valor_credito NUMERIC(15,2),

    urgencia VARCHAR(20),

    stage VARCHAR(50) NOT NULL DEFAULT 'NEW'
        CHECK (stage IN ('NEW','CONTACT','PROPOSAL','NEGOTIATION','CLOSED','LOST')),

    status VARCHAR(50),

    score INT NOT NULL DEFAULT 0
        CHECK (score BETWEEN 0 AND 100),

    owner_email VARCHAR(255),

    resumo_estrategico TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT fk_vendor_leads_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES vendors(id)
        ON DELETE CASCADE
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_vendor_leads_vendor
    ON vendor_leads (vendor_id);

CREATE INDEX idx_vendor_leads_vendor_stage
    ON vendor_leads (vendor_id, stage);

CREATE INDEX idx_vendor_leads_tenant_id
    ON vendor_leads (tenant_id);

CREATE INDEX idx_vendor_leads_tenant_vendor
    ON vendor_leads (tenant_id, vendor_id);

CREATE INDEX idx_vendor_leads_vendor_created
    ON vendor_leads (vendor_id, created_at DESC);

CREATE INDEX idx_vendor_leads_stage
    ON vendor_leads (stage);