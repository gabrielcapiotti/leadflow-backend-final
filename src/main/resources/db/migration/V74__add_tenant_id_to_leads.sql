/* ======================================================
   ADD tenant_id (safe, idempotent)
   ====================================================== */

-- 1) Adiciona coluna como NULL (evita default incorreto)
ALTER TABLE public.leads
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63);

-- 2) Backfill simples para todos os leads existentes
UPDATE public.leads
SET tenant_id = 'public'
WHERE tenant_id IS NULL;

-- 3) Agora sim: NOT NULL
ALTER TABLE public.leads
ALTER COLUMN tenant_id SET NOT NULL;

-- 4) Índices (idempotentes)
CREATE INDEX IF NOT EXISTS idx_leads_tenant_id
    ON public.leads (tenant_id);

CREATE INDEX IF NOT EXISTS idx_leads_tenant_user
    ON public.leads (tenant_id, user_id);