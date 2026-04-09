/* ======================================================
   V78__ensure_stripe_event_logs_structure.sql (DETERMINISTIC)
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

-- ======================================================
-- ENSURE COLUMNS
-- ======================================================

ALTER TABLE public.stripe_event_logs
    ADD COLUMN IF NOT EXISTS tenant_id UUID,
    ADD COLUMN IF NOT EXISTS customer_id VARCHAR(100) DEFAULT 'unknown';

-- ======================================================
-- ENSURE INDEXES
-- ======================================================

CREATE INDEX IF NOT EXISTS idx_stripe_event_logs_tenant_id 
    ON public.stripe_event_logs (tenant_id);

CREATE INDEX IF NOT EXISTS idx_stripe_event_logs_tenant_status 
    ON public.stripe_event_logs (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_stripe_event_logs_customer_id 
    ON public.stripe_event_logs (customer_id);

CREATE INDEX IF NOT EXISTS idx_stripe_event_logs_retry 
    ON public.stripe_event_logs (status, next_retry_at)
    WHERE status IN ('PENDING', 'RETRY_PENDING');

-- ======================================================
-- CONVERT TIMESTAMP TYPES (Direct - will fail if not needed)
-- ======================================================

ALTER TABLE public.stripe_event_logs
ALTER COLUMN next_retry_at TYPE TIMESTAMPTZ
USING next_retry_at AT TIME ZONE 'UTC';

ALTER TABLE public.stripe_event_logs
ALTER COLUMN processed_at TYPE TIMESTAMPTZ
USING processed_at AT TIME ZONE 'UTC';