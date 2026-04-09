/* ======================================================
   V46__create_login_audit.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.login_audit (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID,

    email VARCHAR(255),

    ip_address VARCHAR(100),

    user_agent TEXT,

    success BOOLEAN NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    -- ✅ CONSOLIDADO DE V59
    failure_reason VARCHAR(255),

    suspicious BOOLEAN NOT NULL DEFAULT FALSE,

    tenant_id UUID
);

-- lookup por usuário
CREATE INDEX idx_login_audit_user
    ON public.login_audit (user_id);

-- lookup por email
CREATE INDEX idx_login_audit_email
    ON public.login_audit (email);

-- timeline
CREATE INDEX idx_login_audit_created
    ON public.login_audit (created_at DESC);

-- sucesso/falha (útil pra segurança)
CREATE INDEX idx_login_audit_success
    ON public.login_audit (success);

-- ✅ CONSOLIDADO DE V59
CREATE INDEX idx_login_audit_tenant
    ON public.login_audit(tenant_id);