-- Create table for subscription audit logs
CREATE TABLE IF NOT EXISTS public.subscription_audits (
    id BIGINT NOT NULL GENERATED ALWAYS AS IDENTITY,
    subscription_id BIGINT NOT NULL,
    tenant_id UUID NOT NULL,
    stripe_subscription_id VARCHAR(255),
    status_from VARCHAR(50) NOT NULL CHECK (status_from IN ('ACTIVE', 'PAST_DUE', 'CANCELLED', 'INCOMPLETE')),
    status_to VARCHAR(50) NOT NULL CHECK (status_to IN ('ACTIVE', 'PAST_DUE', 'CANCELLED', 'INCOMPLETE')),
    reason VARCHAR(255),
    stripe_event_id VARCHAR(255),
    created_at TIMESTAMP(6) NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    CONSTRAINT fk_subscription_audits_tenant FOREIGN KEY (tenant_id) REFERENCES public.tenants(id) ON DELETE CASCADE,
    CONSTRAINT fk_subscription_audits_subscription FOREIGN KEY (subscription_id) REFERENCES public.subscriptions(id) ON DELETE CASCADE
);

-- Create indexes
CREATE INDEX IF NOT EXISTS idx_subscription_audits_subscription_id ON public.subscription_audits (subscription_id);
CREATE INDEX IF NOT EXISTS idx_subscription_audits_tenant_id ON public.subscription_audits (tenant_id);
CREATE INDEX IF NOT EXISTS idx_subscription_audits_created_at ON public.subscription_audits (created_at DESC);
