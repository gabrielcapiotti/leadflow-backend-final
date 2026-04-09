/* ======================================================
   V44__create_vendor_features.sql
   TENANT SCHEMA
   ====================================================== */

CREATE TABLE vendor_features (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    vendor_id UUID NOT NULL,

    feature_key VARCHAR(100) NOT NULL,

    enabled BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_vendor_features_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES vendors(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_vendor_features_vendor_key
        UNIQUE (vendor_id, feature_key)
);

-- features por vendor
CREATE INDEX idx_vendor_features_vendor
    ON vendor_features (vendor_id);

-- features ativas por vendor
CREATE INDEX idx_vendor_features_vendor_enabled
    ON vendor_features (vendor_id, enabled);

-- lookup por feature
CREATE INDEX idx_vendor_features_feature_key
    ON vendor_features (feature_key);