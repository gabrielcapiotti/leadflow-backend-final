/* ======================================================
   V72: Ensure Plans Table is Properly Seeded
   
   Safety net migration to ensure that the plans table 
   has the Leadflow Standard plan configured. This is 
   idempotent and safe to run multiple times.
   
   Required by PlanService.getActivePlan() which throws
   RuntimeException if no active plan exists.
   ====================================================== */

-- Ensure table exists (safety check)
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

-- Ensure Leadflow Standard plan exists with correct config
INSERT INTO public.plans (name, max_leads, max_users, max_ai_executions, active, created_at, updated_at)
VALUES ('Leadflow Standard', 5000, 10, 1000, true, NOW(), NOW())
ON CONFLICT (name) DO UPDATE SET
    max_leads = EXCLUDED.max_leads,
    max_users = EXCLUDED.max_users,
    max_ai_executions = EXCLUDED.max_ai_executions,
    active = true,
    updated_at = NOW();

-- Verification: Log status
DO $$
DECLARE
    v_plan_count INT;
    v_active_count INT;
BEGIN
    SELECT COUNT(*) INTO v_plan_count FROM public.plans;
    SELECT COUNT(*) INTO v_active_count FROM public.plans WHERE active = true;
    
    RAISE NOTICE 'Plans table status: % total plans, % active', v_plan_count, v_active_count;
    
    IF v_active_count = 0 THEN
        RAISE WARNING 'No active plans found! PlanService.getActivePlan() will fail!';
    END IF;
END $$;
