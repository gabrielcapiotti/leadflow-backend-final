/* ======================================================
   V27__create_settings.sql
   TENANT SCHEMA
   ====================================================== */

CREATE TABLE settings (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id UUID NOT NULL,
    user_id UUID,

    vendor_name VARCHAR(100) NOT NULL,
    whatsapp VARCHAR(15) NOT NULL,

    company_name VARCHAR(100),
    logo TEXT,
    welcome_message TEXT,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    deleted_at TIMESTAMPTZ,

    CONSTRAINT fk_settings_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    CONSTRAINT uq_settings_tenant_user UNIQUE (tenant_id, user_id)
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_settings_tenant
    ON settings (tenant_id);

CREATE INDEX idx_settings_tenant_user
    ON settings (tenant_id, user_id);

CREATE INDEX idx_settings_deleted_at
    ON settings (deleted_at);