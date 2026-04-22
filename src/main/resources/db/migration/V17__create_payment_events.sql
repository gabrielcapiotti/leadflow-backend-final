/* ======================================================
   V17__create_payment_events.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.payment_events (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id UUID NOT NULL,

    provider_event_id VARCHAR(255) NOT NULL,
    provider VARCHAR(100) NOT NULL,

    processed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_payment_events_provider_event
        UNIQUE (provider, provider_event_id),

    CONSTRAINT fk_payment_events_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(id)
        ON DELETE CASCADE
);

/* ======================================================
   INDEXES
   ====================================================== */

-- tenant isolation
CREATE INDEX idx_payment_events_tenant_id
    ON public.payment_events (tenant_id);

-- consultas por tempo (cleanup / auditoria)
CREATE INDEX idx_payment_events_processed_at
    ON public.payment_events (processed_at);

-- lookup por provider
CREATE INDEX idx_payment_events_provider
    ON public.payment_events (provider);

-- provider + tenant
CREATE INDEX idx_payment_events_provider_tenant
    ON public.payment_events (provider, tenant_id);