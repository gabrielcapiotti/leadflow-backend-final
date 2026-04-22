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

    -- ✅ CONSOLIDADO DE V43
    subject VARCHAR(255),

    html_content TEXT,

    status VARCHAR(30) NOT NULL DEFAULT 'RECEIVED',

    attempt_count INTEGER NOT NULL DEFAULT 0,

    max_attempts INTEGER NOT NULL DEFAULT 5,

    next_retry_at TIMESTAMPTZ,

    processed_at TIMESTAMPTZ,

    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

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

-- ✅ CONSOLIDADO DE V43
CREATE INDEX idx_email_events_status_retry
    ON public.email_events (status, next_retry_at);