/* ======================================================
   ADD tenant_id TO USERS TABLE (MULTI-TENANT)
   ====================================================== */

-- 1) Adiciona coluna como NULL (evita default incorreto)
ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63);

-- 2) Backfill simples: todos os usuários vão para 'public' por padrão
UPDATE public.users
SET tenant_id = 'public'
WHERE tenant_id IS NULL;

-- 3) Agora sim: NOT NULL constraint
ALTER TABLE public.users
ALTER COLUMN tenant_id SET NOT NULL;

-- 4) Índice para queries por tenant
CREATE INDEX IF NOT EXISTS idx_users_tenant_id
    ON public.users (tenant_id);

-- 5) Índice composto para isolamento
CREATE INDEX IF NOT EXISTS idx_users_tenant_email
    ON public.users (tenant_id, email);
