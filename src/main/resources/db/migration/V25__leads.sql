CREATE TABLE leads (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    /* ========== MULTI-TENANT (COLUMN-BASED) ========== */

    tenant_id UUID NOT NULL,

    /* ========== RELACIONAMENTOS ========== */

    vendor_id UUID NOT NULL,
    user_id UUID,

    /* ========== DADOS DO LEAD ========== */

    name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),

    /* ========== STATUS ========== */

    status VARCHAR(30) NOT NULL DEFAULT 'NEW'
        CHECK (status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'CLOSED')),

    /* ========== AUDITORIA ========== */

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    /* ========== CONSTRAINTS ========== */

    CONSTRAINT fk_leads_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES vendors(id)
        ON DELETE CASCADE
);

CREATE INDEX idx_leads_vendor_id
    ON leads (vendor_id);

CREATE INDEX idx_leads_status
    ON leads (status);

CREATE INDEX idx_leads_deleted_at
    ON leads (deleted_at);

CREATE INDEX idx_leads_user_id
    ON leads (user_id);

CREATE INDEX idx_leads_tenant_id
    ON leads (tenant_id);

CREATE INDEX idx_leads_tenant_email
    ON leads (tenant_id, email);