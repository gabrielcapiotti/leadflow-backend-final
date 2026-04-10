CREATE TABLE public.subscriptions (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id UUID NOT NULL,

    stripe_customer_id VARCHAR(255), -- ✅ corrigido
    stripe_subscription_id VARCHAR(255),

    plan_id UUID NOT NULL,

    email VARCHAR(255),

    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE'
        CHECK (
            status IN (
                'ACTIVE',
                'PAST_DUE',
                'CANCELLED',
                'INCOMPLETE',
                'TRIALING',
                'COMPLETED'
            )
        ),

    started_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    last_payment_date TIMESTAMPTZ, -- já correto
    cancelled_at TIMESTAMPTZ,      -- já correto

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_subscription_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_subscription_plan
        FOREIGN KEY (plan_id)
        REFERENCES public.plans(id)
        ON DELETE RESTRICT
);

-- Índices

CREATE UNIQUE INDEX idx_subscription_stripe_subscription_id
ON public.subscriptions(stripe_subscription_id);

CREATE UNIQUE INDEX uq_subscription_stripe_customer_id
ON public.subscriptions (stripe_customer_id)
WHERE stripe_customer_id IS NOT NULL;

CREATE INDEX idx_subscription_email
ON public.subscriptions(email);

CREATE INDEX idx_subscription_tenant_id
ON public.subscriptions(tenant_id);

CREATE INDEX idx_subscription_status
ON public.subscriptions(status);