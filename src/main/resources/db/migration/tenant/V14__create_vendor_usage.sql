/* ======================================================
   V14__create_vendor_usage.sql
   MIGRATED TO PUBLIC SCHEMA with column-based multi-tenancy
   ====================================================== */

CREATE TABLE public.vendor_usage (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    vendor_id UUID NOT NULL,

    tenant_id UUID NOT NULL,

    quota_type VARCHAR(50) NOT NULL,
    used INTEGER NOT NULL DEFAULT 0 CHECK (used >= 0),

    period_start TIMESTAMPTZ NOT NULL,
    period_end TIMESTAMPTZ NOT NULL,

    CONSTRAINT fk_vendor_usage_vendor
        FOREIGN KEY (vendor_id)
        REFERENCES public.vendors(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_vendor_usage_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(id)
        ON DELETE CASCADE,

    -- garante integridade temporal
    CONSTRAINT chk_vendor_usage_period
        CHECK (period_end > period_start)
);

/* ======================================================
   INDEXES
   ======================================================  */

-- Multi-tenant isolation first
CREATE INDEX idx_vendor_usage_tenant
    ON public.vendor_usage (tenant_id);

-- Um registro por vendor + tipo + período (evita conflito mensal)
CREATE UNIQUE INDEX idx_vendor_usage_vendor_quota_period
    ON public.vendor_usage (vendor_id, quota_type, period_start);

-- consultas de período (billing/reset)
CREATE INDEX idx_vendor_usage_period
    ON public.vendor_usage (period_start, period_end);

-- lookup por vendor
CREATE INDEX IF NOT EXISTS idx_vendor_usage_vendor
    ON public.vendor_usage (vendor_id);