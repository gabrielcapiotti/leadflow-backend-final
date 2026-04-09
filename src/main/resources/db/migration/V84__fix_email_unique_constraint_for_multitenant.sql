/* ======================================================
   V84__fix_users_email_unique_multitenant.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

-- 1. DROP constraint antiga (global)
ALTER TABLE public.users
DROP CONSTRAINT IF EXISTS uq_users_email;

-- 2. ADD constraint correta (tenant-aware)
ALTER TABLE public.users
ADD CONSTRAINT uq_users_email_tenant
UNIQUE (email, tenant_id);

-- 3. INDEX para performance (queries por tenant + email)
CREATE INDEX IF NOT EXISTS idx_users_tenant_email
    ON public.users (tenant_id, email);