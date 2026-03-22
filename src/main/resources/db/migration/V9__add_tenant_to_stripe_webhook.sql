/* ======================================================
   V10__add_tenant_to_stripe_webhook.sql
   Add tenant isolation + customer tracking to webhooks
   ====================================================== */

-- Add tenant_id and customer_id columns to stripe_event_logs
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
