/* ======================================================
   V59__enhance_login_audit.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

-- Adiciona colunas novas apenas (não existentes no V46)

ALTER TABLE public.login_audit
ADD COLUMN failure_reason VARCHAR(255);

ALTER TABLE public.login_audit
ADD COLUMN suspicious BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE public.login_audit
ADD COLUMN tenant_id UUID;

-- Índices (idempotentes para evitar duplicação)

CREATE INDEX IF NOT EXISTS idx_login_audit_user
    ON public.login_audit(user_id);

CREATE INDEX IF NOT EXISTS idx_login_audit_tenant
    ON public.login_audit(tenant_id);

CREATE INDEX IF NOT EXISTS idx_login_audit_created
    ON public.login_audit(created_at DESC);