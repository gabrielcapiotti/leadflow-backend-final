/* ======================================================
   REFRESH TOKENS
   Multi-device token refresh storage with device fingerprinting
   ====================================================== */

CREATE TABLE IF NOT EXISTS public.refresh_tokens (

    id UUID NOT NULL,

    user_id UUID NOT NULL,

    token_hash VARCHAR(255) NOT NULL,

    device_fingerprint VARCHAR(255) NOT NULL,

    expires_at TIMESTAMPTZ NOT NULL,

    revoked BOOLEAN NOT NULL DEFAULT FALSE,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT pk_refresh_tokens
        PRIMARY KEY (id),

    CONSTRAINT uq_refresh_tokens_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_refresh_tokens_user
        FOREIGN KEY (user_id)
        REFERENCES public.users(id)
        ON DELETE CASCADE
);


/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user
    ON public.refresh_tokens(user_id);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_hash
    ON public.refresh_tokens(token_hash);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_fingerprint
    ON public.refresh_tokens(device_fingerprint);

CREATE INDEX IF NOT EXISTS idx_refresh_tokens_expires
    ON public.refresh_tokens(expires_at);

-- Composite index for common queries
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_user_revoked
    ON public.refresh_tokens(user_id, revoked);