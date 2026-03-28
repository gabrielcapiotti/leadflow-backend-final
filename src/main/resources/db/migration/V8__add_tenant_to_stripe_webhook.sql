/* ======================================================
   V10__add_tenant_to_stripe_webhook.sql
   Add tenant isolation + customer tracking to webhooks
   ====================================================== */

-- Create table if not exists (guard for early migrations)
CREATE TABLE IF NOT EXISTS public.stripe_event_logs (
    id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    event_id VARCHAR(100) NOT NULL UNIQUE,
    event_type VARCHAR(100) NOT NULL,
    payload TEXT NOT NULL,
    status VARCHAR(50) NOT NULL CHECK (status IN ('PENDING', 'PROCESSING', 'SUCCESS', 'FAILED', 'RETRY_PENDING')),
    retry_count INTEGER NOT NULL DEFAULT 0,
    max_retries INTEGER NOT NULL DEFAULT 3,
    next_retry_at TIMESTAMP(6),
    last_error TEXT,
    processed_at TIMESTAMP(6),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6),
    tenant_id UUID DEFAULT NULL,
    customer_id VARCHAR(100) DEFAULT 'unknown',
    PRIMARY KEY (id)
);

-- Add tenant_id and customer_id columns to stripe_event_logs (if table already exists)
ALTER TABLE public.stripe_event_logs
    ADD COLUMN IF NOT EXISTS tenant_id UUID DEFAULT NULL,
    ADD COLUMN IF NOT EXISTS customer_id VARCHAR(100) DEFAULT 'unknown';

-- Create indexes for tenant isolation and queries
CREATE INDEX IF NOT EXISTS idx_stripe_event_logs_tenant_id 
    ON public.stripe_event_logs(tenant_id);

CREATE INDEX IF NOT EXISTS idx_stripe_event_logs_tenant_status 
    ON public.stripe_event_logs(tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_stripe_event_logs_customer_id 
    ON public.stripe_event_logs(customer_id);

-- Composite index for efficient retry queries by tenant
CREATE INDEX IF NOT EXISTS idx_stripe_event_logs_tenant_retry 
    ON public.stripe_event_logs(tenant_id, status, next_retry_at)
    WHERE status = 'RETRY_PENDING';

-- Comment for documentation
COMMENT ON COLUMN public.stripe_event_logs.tenant_id 
    IS 'Tenant UUID for multi-tenant isolation - extracted from Stripe Customer metadata';

COMMENT ON COLUMN public.stripe_event_logs.customer_id 
    IS 'Stripe Customer ID for event traceability - enables quick customer lookup';
