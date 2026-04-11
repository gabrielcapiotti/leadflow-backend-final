/* ======================================================
   V37__create_vendor_lead_alerts.sql
   TENANT SCHEMA
   ====================================================== */

CREATE TABLE vendor_lead_alerts (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    vendor_lead_id UUID NOT NULL,

    type VARCHAR(50) NOT NULL
        CHECK (type IN ('RISK', 'FOLLOW_UP', 'AI_ALERT', 'SYSTEM')),

    message TEXT NOT NULL,

    resolved BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_vendor_lead_alerts_lead
        FOREIGN KEY (vendor_lead_id)
        REFERENCES vendor_leads(id)
        ON DELETE CASCADE
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_vendor_lead_alerts_lead
    ON vendor_lead_alerts (vendor_lead_id);

CREATE INDEX idx_vendor_lead_alerts_resolved
    ON vendor_lead_alerts (resolved);

CREATE INDEX idx_vendor_lead_alerts_created
    ON vendor_lead_alerts (created_at DESC);