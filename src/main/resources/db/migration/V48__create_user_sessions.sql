/* ======================================================
   V48__create_user_sessions.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.user_sessions (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,

    tenant_id UUID,

    token_id VARCHAR(255) NOT NULL UNIQUE,

    active BOOLEAN NOT NULL DEFAULT TRUE,

    suspicious BOOLEAN NOT NULL DEFAULT FALSE,

    ip_address VARCHAR(100),
    initial_ip_address VARCHAR(100),

    user_agent TEXT,
    initial_user_agent TEXT,

    last_access_at TIMESTAMPTZ,

    revoked_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ,

    CONSTRAINT fk_user_sessions_user
        FOREIGN KEY (user_id)
        REFERENCES public.users(id)
        ON DELETE CASCADE
);

-- lookup por usuário
CREATE INDEX idx_user_sessions_user
    ON public.user_sessions(user_id);

-- sessões ativas por usuário + tenant
CREATE INDEX idx_user_sessions_user_tenant_active
    ON public.user_sessions(user_id, tenant_id)
    WHERE active = TRUE;

-- lookup por token (redundante com UNIQUE, mas ok para leitura)
CREATE INDEX idx_user_sessions_token
    ON public.user_sessions(token_id);

-- timeline de acesso
CREATE INDEX idx_user_sessions_last_access
    ON public.user_sessions(last_access_at DESC);

-- sessões suspeitas
CREATE INDEX idx_user_sessions_suspicious
    ON public.user_sessions(suspicious)
    WHERE suspicious = TRUE;