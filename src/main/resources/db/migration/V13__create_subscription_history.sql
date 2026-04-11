/* ======================================================
   V13__create_subscription_history.sql
   TENANT SCHEMA
   ====================================================== */

CREATE TABLE subscription_history (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    vendor_id UUID NOT NULL,

    previous_status VARCHAR(32) NOT NULL,
    new_status VARCHAR(32) NOT NULL,

    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    reason VARCHAR(255),
    external_event_id VARCHAR(255),

    CONSTRAINT fk_subscription_history_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES vendors (id)
        ON DELETE CASCADE
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_subscription_history_vendor_changed
    ON subscription_history (vendor_id, changed_at DESC);

CREATE INDEX idx_subscription_history_external_event
    ON subscription_history (external_event_id);