/* ======================================================
   V10__create_webhook_alerts.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.webhook_alerts (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    alert_type VARCHAR(50) NOT NULL,

    severity VARCHAR(20) NOT NULL CHECK (
        severity IN ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')
    ),

    tenant_id UUID NOT NULL,

    message TEXT,
    metrics JSONB,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,
    resolved_at TIMESTAMPTZ

);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_webhook_alerts_tenant_created
    ON public.webhook_alerts (tenant_id, created_at DESC);

CREATE INDEX idx_webhook_alerts_severity_created
    ON public.webhook_alerts (severity, created_at DESC);

CREATE INDEX idx_webhook_alerts_active
    ON public.webhook_alerts (resolved_at)
    WHERE resolved_at IS NULL;

CREATE INDEX idx_webhook_alerts_created
    ON public.webhook_alerts (created_at DESC);