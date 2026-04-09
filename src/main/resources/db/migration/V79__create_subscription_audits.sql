/* ======================================================
   V79__create_subscription_audits.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.subscription_audits (

    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,

    -- 🔧 CORRIGIDO
    subscription_id UUID NOT NULL,

    tenant_id UUID NOT NULL,

    stripe_subscription_id VARCHAR(255),

    status_from VARCHAR(50) NOT NULL,
    status_to VARCHAR(50) NOT NULL,

    reason VARCHAR(255),

    stripe_event_id VARCHAR(255),

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_subscription_audits_subscription
        FOREIGN KEY (subscription_id)
        REFERENCES public.subscriptions(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_subscription_audits_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(id)
        ON DELETE CASCADE
);

-- ======================================================
-- INDEXES
-- ======================================================

CREATE INDEX idx_subscription_audits_subscription
    ON public.subscription_audits (subscription_id);

CREATE INDEX idx_subscription_audits_tenant
    ON public.subscription_audits (tenant_id);

CREATE INDEX idx_subscription_audits_created
    ON public.subscription_audits (created_at DESC);

CREATE INDEX idx_subscription_audits_event
    ON public.subscription_audits (stripe_event_id);