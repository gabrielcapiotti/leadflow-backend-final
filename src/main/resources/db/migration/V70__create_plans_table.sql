/* ======================================================
   PLANS TABLE - Single Plan Configuration
   
   LeadFlow currently operates with a single standard plan.
   This table stores the plan configuration for usage limits
   and subscription management.
   ====================================================== */

CREATE TABLE IF NOT EXISTS public.plans (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(100) NOT NULL UNIQUE,
    max_leads INTEGER NOT NULL,
    max_users INTEGER NOT NULL,
    max_ai_executions INTEGER NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP NOT NULL DEFAULT NOW()
);

/* ======================================================
   SEED - LeadFlow Standard Plan
   
   single standard plan with resource limits for all vendors
   ====================================================== */

INSERT INTO public.plans (name, max_leads, max_users, max_ai_executions, active, created_at, updated_at)
VALUES ('Leadflow Standard', 5000, 10, 1000, true, NOW(), NOW())
ON CONFLICT (name) DO UPDATE SET
    max_leads = EXCLUDED.max_leads,
    max_users = EXCLUDED.max_users,
    max_ai_executions = EXCLUDED.max_ai_executions,
    active = EXCLUDED.active,
    updated_at = NOW();

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX IF NOT EXISTS idx_plans_active 
    ON public.plans (active) 
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_plans_name
    ON public.plans (name);
