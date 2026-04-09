/* ======================================================
   V90__create_stripe_customer_mapping.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.stripe_customers (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    -- 🔧 CORRIGIDO: aponta para tenants (global)
    tenant_id UUID NOT NULL
        REFERENCES public.tenants(id)
        ON DELETE CASCADE,

    stripe_customer_id VARCHAR(255) NOT NULL UNIQUE,

    -- 🔧 CORRIGIDO: tipo UUID
    subscription_id UUID
        REFERENCES public.subscriptions(id)
        ON DELETE SET NULL,

    status VARCHAR(50) DEFAULT 'active',

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_webhook_at TIMESTAMPTZ,

    metadata JSONB
);

-- INDEXES

CREATE INDEX idx_stripe_customers_tenant
    ON public.stripe_customers (tenant_id);

CREATE INDEX idx_stripe_customers_subscription
    ON public.stripe_customers (subscription_id);

COMMENT ON TABLE public.stripe_customers 
IS 'Maps Stripe customer_id → tenant. Used for stateless webhook processing.';