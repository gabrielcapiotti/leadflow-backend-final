/* ======================================================
   V81__create_failed_webhook_events.sql (DETERMINISTIC)
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.failed_webhook_events (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    stripe_event_id VARCHAR(255) NOT NULL UNIQUE,

    event_type VARCHAR(255) NOT NULL,

    event_data TEXT NOT NULL,

    failure_reason TEXT NOT NULL,

    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,

    status VARCHAR(50) NOT NULL,

    next_retry_at TIMESTAMPTZ NOT NULL,
    original_received_at TIMESTAMPTZ NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    succeeded_at TIMESTAMPTZ,

    tenant_id UUID NOT NULL,

    CONSTRAINT fk_failed_webhook_events_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(id)
        ON DELETE CASCADE
);

-- ======================================================
-- INDEXES
-- ======================================================

CREATE INDEX idx_failed_webhook_status
    ON public.failed_webhook_events (status);

CREATE INDEX idx_failed_webhook_created
    ON public.failed_webhook_events (created_at DESC);

CREATE INDEX idx_failed_webhook_retry
    ON public.failed_webhook_events (status, next_retry_at);

CREATE INDEX idx_failed_webhook_tenant_id
    ON public.failed_webhook_events (tenant_id);

CREATE INDEX idx_failed_webhook_status_tenant
    ON public.failed_webhook_events (status, tenant_id);