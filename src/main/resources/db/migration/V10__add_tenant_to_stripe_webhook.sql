/* ======================================================
   V8__create_stripe_event_logs.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.stripe_event_logs (

    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    event_id VARCHAR(100) NOT NULL,
    event_type VARCHAR(100) NOT NULL,

    payload TEXT NOT NULL,

    status VARCHAR(50) NOT NULL CHECK (
        status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'RETRY_PENDING')
    ),

    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,

    next_retry_at TIMESTAMPTZ,
    last_error TEXT,
    processed_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,

    tenant_id UUID,
    customer_id VARCHAR(100) DEFAULT 'unknown',

    CONSTRAINT uq_stripe_event_logs_event_id UNIQUE (event_id)
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_stripe_event_logs_tenant_id
    ON public.stripe_event_logs (tenant_id);

CREATE INDEX idx_stripe_event_logs_tenant_status
    ON public.stripe_event_logs (tenant_id, status);

CREATE INDEX idx_stripe_event_logs_customer_id
    ON public.stripe_event_logs (customer_id);

CREATE INDEX idx_stripe_event_logs_retry 
    ON public.stripe_event_logs (status, next_retry_at)
    WHERE status IN ('PENDING', 'RETRY_PENDING');

CREATE INDEX idx_stripe_event_logs_tenant_retry
    ON public.stripe_event_logs (tenant_id, status, next_retry_at)
    WHERE status = 'RETRY_PENDING';

/* ======================================================
   COMMENTS
   ====================================================== */

COMMENT ON COLUMN public.stripe_event_logs.tenant_id
    IS 'Tenant UUID for multi-tenant isolation - extracted from Stripe Customer metadata';

COMMENT ON COLUMN public.stripe_event_logs.customer_id
    IS 'Stripe Customer ID for event traceability - enables quick customer lookup';