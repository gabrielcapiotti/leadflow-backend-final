/* ======================================================
   V26__create_lead_status_history.sql
   TENANT SCHEMA
   ====================================================== */

CREATE TABLE lead_status_history (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id UUID NOT NULL,

    lead_id UUID NOT NULL,

    status VARCHAR(30) NOT NULL
        CHECK (status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'CLOSED')),

    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    changed_by UUID,

    CONSTRAINT fk_lead_status_history_lead
        FOREIGN KEY (lead_id)
        REFERENCES leads(id)
        ON DELETE CASCADE
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_lsh_tenant_id
    ON lead_status_history (tenant_id);

CREATE INDEX idx_lsh_lead_id
    ON lead_status_history (lead_id);

CREATE INDEX idx_lsh_lead_tenant
    ON lead_status_history (lead_id, tenant_id);

CREATE INDEX idx_lsh_lead_changed_at
    ON lead_status_history (lead_id, changed_at DESC);

CREATE INDEX idx_lsh_status
    ON lead_status_history (status);

CREATE INDEX idx_lsh_changed_by
    ON lead_status_history (changed_by);