/* ======================================================
   V40__create_vendor_lead_stage_history.sql
   TENANT SCHEMA
   ====================================================== */

CREATE TABLE vendor_lead_stage_history (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    vendor_lead_id UUID NOT NULL,

    previous_stage VARCHAR(50) NOT NULL,
    new_stage VARCHAR(50) NOT NULL
        CHECK (new_stage IN ('NEW','CONTACT','PROPOSAL','NEGOTIATION','CLOSED','LOST')),

    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_vendor_lead_stage_history_lead
        FOREIGN KEY (vendor_lead_id)
        REFERENCES vendor_leads(id)
        ON DELETE CASCADE
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_vlsh_lead_changed
    ON vendor_lead_stage_history (vendor_lead_id, changed_at DESC);

CREATE INDEX idx_vlsh_new_stage
    ON vendor_lead_stage_history (new_stage);