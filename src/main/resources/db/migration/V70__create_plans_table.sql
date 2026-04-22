/* ======================================================
   V70__create_plans_table.sql (DETERMINISTIC VERSION)

   Deterministic guarantees:
   - Fixed primary key for default plan
   - Idempotent seed (UPSERT)
   - No reliance on random UUID for critical data
   ====================================================== */

CREATE TABLE IF NOT EXISTS public.plans (
    id UUID PRIMARY KEY,

    name VARCHAR(100) NOT NULL,
    code VARCHAR(50) NOT NULL,

    max_leads INTEGER NOT NULL,
    max_users INTEGER NOT NULL,
    max_ai_executions INTEGER NOT NULL,

    stripe_product_id VARCHAR(255),
    stripe_price_id VARCHAR(255),

    active BOOLEAN NOT NULL DEFAULT TRUE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

-- Unique constraints (safe creation)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_plans_name'
    ) THEN
        ALTER TABLE public.plans ADD CONSTRAINT uq_plans_name UNIQUE (name);
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'uq_plans_code'
    ) THEN
        ALTER TABLE public.plans ADD CONSTRAINT uq_plans_code UNIQUE (code);
    END IF;
END$$;

-- Comments (idempotent)
COMMENT ON COLUMN public.plans.code
IS 'Stable identifier for plan. Currently only STANDARD is supported.';

COMMENT ON COLUMN public.plans.stripe_product_id
IS 'Stripe product ID for billing integration';

COMMENT ON COLUMN public.plans.stripe_price_id
IS 'Stripe price ID for billing integration';

-- Deterministic seed (UPSERT by ID)
INSERT INTO public.plans (
    id,
    name,
    code,
    max_leads,
    max_users,
    max_ai_executions,
    stripe_product_id,
    stripe_price_id,
    active,
    created_at,
    updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000100'::uuid,
    'LEADFLOW_STANDARD',
    'STANDARD',
    5000,
    10,
    1000,
    'prod_UNEkkapYiKhRQ1',
    'price_1TOUGrFzdxPQXW4wpPIt0FV7',
    TRUE,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    code = EXCLUDED.code,
    max_leads = EXCLUDED.max_leads,
    max_users = EXCLUDED.max_users,
    max_ai_executions = EXCLUDED.max_ai_executions,
    stripe_product_id = EXCLUDED.stripe_product_id,
    stripe_price_id = EXCLUDED.stripe_price_id,
    active = EXCLUDED.active,
    updated_at = CURRENT_TIMESTAMP;

-- Indexes (idempotent)
CREATE INDEX IF NOT EXISTS idx_plans_active ON public.plans (active);
CREATE INDEX IF NOT EXISTS idx_plans_name ON public.plans (name);
CREATE INDEX IF NOT EXISTS idx_plans_code ON public.plans (code);
CREATE INDEX IF NOT EXISTS idx_plans_stripe_product_id ON public.plans (stripe_product_id);
CREATE INDEX IF NOT EXISTS idx_plans_stripe_price_id ON public.plans (stripe_price_id);