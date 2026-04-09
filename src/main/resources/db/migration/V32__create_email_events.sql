/* ======================================================
   V32__create_email_events.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.email_events (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id UUID NOT NULL,

    email VARCHAR(255) NOT NULL,

    event_type VARCHAR(100) NOT NULL,

    occurred_at TIMESTAMPTZ NOT NULL,

    reason TEXT,

    CONSTRAINT fk_email_events_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(id)
        ON DELETE CASCADE
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_email_events_tenant_id
    ON public.email_events (tenant_id);

CREATE INDEX idx_email_events_email
    ON public.email_events (email);

CREATE INDEX idx_email_events_event_type
    ON public.email_events (event_type);

CREATE INDEX idx_email_events_occurred_at
    ON public.email_events (occurred_at DESC);

CREATE INDEX idx_email_events_email_type
    ON public.email_events (email, event_type);

CREATE INDEX idx_email_events_email_tenant
    ON public.email_events (email, tenant_id);