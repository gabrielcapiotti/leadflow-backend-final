/* ======================================================
   V44__create_vendor_features.sql
   TENANT SCHEMA
   ====================================================== */

CREATE TABLE vendor_features (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id UUID NOT NULL,

    vendor_id UUID NOT NULL,

    feature_key VARCHAR(100) NOT NULL,

    enabled BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_vendor_features_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_vendor_features_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES vendors(id)
        ON DELETE CASCADE,

    CONSTRAINT uq_vendor_features_vendor_key_tenant
        UNIQUE (vendor_id, feature_key, tenant_id)
);

-- Tenant isolation index
CREATE INDEX idx_vendor_features_tenant
    ON vendor_features (tenant_id);

-- Features per tenant+vendor
CREATE INDEX idx_vendor_features_tenant_vendor
    ON vendor_features (tenant_id, vendor_id);

-- Feature lookup by key within tenant
CREATE INDEX idx_vendor_features_tenant_feature_key
    ON vendor_features (tenant_id, feature_key);

-- Active features per tenant+vendor (for feature flagging)
CREATE INDEX idx_vendor_features_tenant_vendor_enabled
    ON vendor_features (tenant_id, vendor_id, enabled);