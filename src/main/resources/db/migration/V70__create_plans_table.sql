/* ======================================================
   V70__create_plans.sql (DETERMINISTIC)
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.plans (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    name VARCHAR(100) NOT NULL,

    max_leads INTEGER NOT NULL,
    max_users INTEGER NOT NULL,
    max_ai_executions INTEGER NOT NULL,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE public.plans
ADD CONSTRAINT uq_plans_name UNIQUE (name);

-- Default plan seed
INSERT INTO public.plans (
    id, name, max_leads, max_users, max_ai_executions, active, created_at, updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000100'::uuid,
    'LEADFLOW_STANDARD',
    5000,
    10,
    1000,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET 
    name = 'LEADFLOW_STANDARD',
    max_leads = 5000,
    max_users = 10,
    max_ai_executions = 1000,
    updated_at = CURRENT_TIMESTAMP;

CREATE INDEX idx_plans_active
ON public.plans (active);

CREATE INDEX idx_plans_name
ON public.plans (name);