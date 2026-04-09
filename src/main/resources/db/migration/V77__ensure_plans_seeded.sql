/* ======================================================
   V77__ensure_standard_plan_exists.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

INSERT INTO public.plans (
    id,
    name,
    max_leads,
    max_users,
    max_ai_executions,
    active,
    created_at,
    updated_at
)
SELECT
    '00000000-0000-0000-0000-000000000100',
    'LEADFLOW_STANDARD',
    5000,
    10,
    1000,
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
WHERE NOT EXISTS (
    SELECT 1 FROM public.plans
    WHERE name = 'LEADFLOW_STANDARD'
);