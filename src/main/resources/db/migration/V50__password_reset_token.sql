/* ======================================================
   V50__create_password_reset_token.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.password_reset_token (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    user_id UUID NOT NULL,

    token_hash VARCHAR(255) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,

    used BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT uq_password_reset_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_password_reset_token_user
        FOREIGN KEY (user_id)
        REFERENCES public.users(id)
        ON DELETE CASCADE
);

-- lookup por usuário
CREATE INDEX idx_password_reset_token_user
    ON public.password_reset_token(user_id);

-- expiração (cleanup / validação)
CREATE INDEX idx_password_reset_token_expires
    ON public.password_reset_token(expires_at);

-- tokens ativos (muito útil)
CREATE INDEX idx_password_reset_token_active
    ON public.password_reset_token(user_id, expires_at)
    WHERE used = FALSE;
    