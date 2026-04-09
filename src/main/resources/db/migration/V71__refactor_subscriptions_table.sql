/* ======================================================
   V71__create_subscriptions.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.subscriptions (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id UUID NOT NULL,

    stripe_customer_id VARCHAR(255) NOT NULL,
    stripe_subscription_id VARCHAR(255),

    plan_id UUID NOT NULL,

    email VARCHAR(255),

    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',

    started_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    last_payment_date TIMESTAMPTZ,
    cancelled_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- ✅ CORRETO
    CONSTRAINT fk_subscription_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_subscription_plan
        FOREIGN KEY (plan_id)
        REFERENCES public.plans(id)
        ON DELETE RESTRICT
);

CREATE UNIQUE INDEX idx_subscription_stripe_subscription_id
ON public.subscriptions(stripe_subscription_id);

CREATE INDEX idx_subscription_stripe_customer_id
ON public.subscriptions(stripe_customer_id);

CREATE INDEX idx_subscription_email
ON public.subscriptions(email);

CREATE INDEX idx_subscription_tenant_id
ON public.subscriptions(tenant_id);

CREATE INDEX idx_subscription_status
ON public.subscriptions(status);