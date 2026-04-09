/* ======================================================
   V93__fix_subscription_status_constraint.sql
   ====================================================== */

-- 1. NORMALIZAR dados existentes
UPDATE public.subscriptions
SET status = UPPER(status)
WHERE status IS NOT NULL;

-- 2. REMOVER constraint antiga (idempotente)
ALTER TABLE public.subscriptions
DROP CONSTRAINT IF EXISTS subscriptions_status_check;

-- 3. ADICIONAR nova constraint
ALTER TABLE public.subscriptions
ADD CONSTRAINT subscriptions_status_check
CHECK (
    status IN (
        'ACTIVE',
        'PAST_DUE',
        'CANCELLED',
        'INCOMPLETE',
        'TRIALING',
        'COMPLETED'
    )
);