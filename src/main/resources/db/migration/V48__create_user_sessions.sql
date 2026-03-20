/* ======================================================
   USER SESSIONS
   Tracks user session tokens for multi-device support
   ====================================================== */

CREATE TABLE IF NOT EXISTS public.user_sessions (

    id UUID NOT NULL,

    user_id UUID NOT NULL,

    tenant_id UUID,

    token_id VARCHAR(255) NOT NULL,

    active BOOLEAN DEFAULT TRUE,

    suspicious BOOLEAN DEFAULT FALSE,

    ip_address VARCHAR(100),

    initial_ip_address VARCHAR(100),

    user_agent TEXT,

    initial_user_agent TEXT,

    last_access_at TIMESTAMPTZ,

    revoked_at TIMESTAMPTZ,

    created_at TIMESTAMPTZ NOT NULL,

    updated_at TIMESTAMPTZ,

    CONSTRAINT pk_user_sessions PRIMARY KEY (id),

    CONSTRAINT fk_user_sessions_user
        FOREIGN KEY (user_id)
        REFERENCES public.users(id)
        ON DELETE CASCADE
);

-- =========================
-- Indexes
-- =========================

CREATE INDEX IF NOT EXISTS idx_session_user
    ON public.user_sessions(user_id);

CREATE INDEX IF NOT EXISTS idx_session_token_id
    ON public.user_sessions(token_id);

CREATE INDEX IF NOT EXISTS idx_session_token_tenant
    ON public.user_sessions(token_id, tenant_id);

CREATE INDEX IF NOT EXISTS idx_session_user_tenant
    ON public.user_sessions(user_id, tenant_id);

CREATE INDEX IF NOT EXISTS idx_session_user_tenant_active
    ON public.user_sessions(user_id, tenant_id, active);

CREATE INDEX IF NOT EXISTS idx_session_last_access
    ON public.user_sessions(last_access_at);

CREATE INDEX IF NOT EXISTS idx_session_active
    ON public.user_sessions(active);

CREATE INDEX IF NOT EXISTS idx_session_suspicious
    ON public.user_sessions(suspicious);