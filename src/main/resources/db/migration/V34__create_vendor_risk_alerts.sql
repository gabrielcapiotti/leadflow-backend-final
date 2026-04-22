/* ======================================================
   V34__create_vendor_risk_alerts.sql
   TENANT SCHEMA
   ====================================================== */

CREATE TABLE vendor_risk_alerts (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    vendor_id UUID NOT NULL,

    score INTEGER NOT NULL,

    risk_level VARCHAR(20) NOT NULL
        CHECK (risk_level IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    resolved BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT fk_vendor_risk_alerts_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES vendors(id)
        ON DELETE CASCADE
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_vendor_risk_alerts_vendor_resolved
    ON vendor_risk_alerts (vendor_id, resolved);

CREATE INDEX idx_vendor_risk_alerts_created
    ON vendor_risk_alerts (created_at DESC);

CREATE INDEX idx_vendor_risk_alerts_risk_level
    ON vendor_risk_alerts (risk_level);