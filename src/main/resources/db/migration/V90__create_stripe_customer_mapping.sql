/* ======================================================
   V90__create_stripe_customer_mapping.sql
   Create critical mapping table: Stripe Customer → Tenant
   ====================================================== */

-- SOURCE OF TRUTH for webhook processing
-- Maps Stripe customer_id to local tenant_id
-- Essential for stateless, reliable webhook handling
CREATE TABLE IF NOT EXISTS public.stripe_customers (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL REFERENCES public.tenants(id) ON DELETE CASCADE,
    stripe_customer_id VARCHAR(255) NOT NULL UNIQUE,
    subscription_id BIGINT REFERENCES public.subscriptions(id) ON DELETE SET NULL,
    status VARCHAR(50) DEFAULT 'active' CHECK (status IN ('active', 'inactive', 'deleted')),
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW(),
    last_webhook_at TIMESTAMP,
    metadata JSONB
);

-- Index for fast lookup by stripe_customer_id (webhook path)
CREATE INDEX idx_stripe_customers_stripe_customer_id 
    ON public.stripe_customers(stripe_customer_id);

-- Index for tenant-based queries (reporting)
CREATE INDEX idx_stripe_customers_tenant_id 
    ON public.stripe_customers(tenant_id);

-- Index for subscription correlation
CREATE INDEX idx_stripe_customers_subscription_id 
    ON public.stripe_customers(subscription_id);

-- Composite index: tenant + stripe_id (security: prevent cross-tenant leaks)
CREATE UNIQUE INDEX idx_stripe_customers_tenant_stripe_unique 
    ON public.stripe_customers(tenant_id, stripe_customer_id);

-- Add comment
COMMENT ON TABLE public.stripe_customers 
    IS 'Critical mapping table: resolves Stripe customer_id → tenant_id. Used by webhook handlers for stateless, reliable processing.';

COMMENT ON COLUMN public.stripe_customers.stripe_customer_id 
    IS 'Stripe API customer ID (e.g., cus_xxx). Primary key for webhook lookups.';

COMMENT ON COLUMN public.stripe_customers.tenant_id 
    IS 'Local tenant UUID. Resolved at webhook time to isolate multi-tenant data.';

COMMENT ON COLUMN public.stripe_customers.subscription_id 
    IS 'Optional reference to local subscription. For subscription-specific webhooks.';

COMMENT ON COLUMN public.stripe_customers.status 
    IS 'Lifecycle: active (normal), inactive (disabled), deleted (cleanup marker)';

COMMENT ON COLUMN public.stripe_customers.last_webhook_at 
    IS 'Timestamp of last webhook processed. For monitoring/debugging.';
