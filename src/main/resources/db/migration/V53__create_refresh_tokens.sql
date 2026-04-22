/* ======================================================
   V53__create_refresh_tokens.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.refresh_tokens (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,

    tenant_id UUID NOT NULL,

    token_hash VARCHAR(255) NOT NULL,

    device_fingerprint VARCHAR(255) NOT NULL,

    expires_at TIMESTAMPTZ NOT NULL,

    revoked BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT uq_refresh_tokens_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES public.users(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_refresh_tokens_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(id)
        ON DELETE CASCADE
);

-- lookup por usuário
CREATE INDEX idx_refresh_tokens_user
    ON public.refresh_tokens(user_id);

-- lookup por tenant
CREATE INDEX idx_refresh_tokens_tenant
    ON public.refresh_tokens(tenant_id);

-- lookup por fingerprint
CREATE INDEX idx_refresh_tokens_fingerprint
    ON public.refresh_tokens(device_fingerprint);

-- expiração
CREATE INDEX idx_refresh_tokens_expires
    ON public.refresh_tokens(expires_at);

-- tokens ativos por usuário
CREATE INDEX idx_refresh_tokens_user_active
    ON public.refresh_tokens(user_id)
    WHERE revoked = FALSE;