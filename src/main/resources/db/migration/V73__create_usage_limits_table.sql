/* ======================================================
   V73__create_usage_limits.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.usage_limits (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    tenant_id UUID NOT NULL UNIQUE,

    leads_used INTEGER NOT NULL DEFAULT 0,
    users_used INTEGER NOT NULL DEFAULT 0,
    ai_executions_used INTEGER NOT NULL DEFAULT 0,

    plan_id UUID NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,

    CONSTRAINT fk_usage_limit_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_usage_limit_plan
        FOREIGN KEY (plan_id)
        REFERENCES public.plans(id)
        ON DELETE RESTRICT,

    CONSTRAINT chk_leads_used_positive CHECK (leads_used >= 0),
    CONSTRAINT chk_users_used_positive CHECK (users_used >= 0),
    CONSTRAINT chk_ai_exec_used_positive CHECK (ai_executions_used >= 0)
);

CREATE INDEX idx_usage_limit_tenant_id
ON public.usage_limits(tenant_id);

CREATE INDEX idx_usage_limit_plan_id
ON public.usage_limits(plan_id);