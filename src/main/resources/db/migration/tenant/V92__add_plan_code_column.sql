/* ======================================================
   V92__add_plan_code.sql
   Single plan (STANDARD)
   ====================================================== */

-- 1. Add column (idempotente)
ALTER TABLE public.plans
ADD COLUMN IF NOT EXISTS code VARCHAR(50);

-- 2. Garantir que todos os registros tenham STANDARD
UPDATE public.plans
SET code = 'STANDARD'
WHERE code IS NULL;

-- 3. UNIQUE constraint
ALTER TABLE public.plans
ADD CONSTRAINT uq_plans_code UNIQUE (code);

-- 4. NOT NULL seguro
ALTER TABLE public.plans
ALTER COLUMN code SET NOT NULL;

-- 5. Documentação
COMMENT ON COLUMN public.plans.code
IS 'Stable identifier for plan. Currently only STANDARD is supported.';