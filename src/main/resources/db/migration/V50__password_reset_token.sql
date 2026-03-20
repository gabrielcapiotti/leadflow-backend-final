/* ======================================================
   PASSWORD RESET TOKEN
   Secure token for password reset functionality
   ====================================================== */

CREATE TABLE IF NOT EXISTS public.password_reset_token (

    id UUID NOT NULL,

    user_id UUID NOT NULL,

    token_hash VARCHAR(255) NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    expires_at TIMESTAMPTZ NOT NULL,

    used BOOLEAN NOT NULL DEFAULT FALSE,

    CONSTRAINT pk_password_reset_token
        PRIMARY KEY (id),

    CONSTRAINT uq_password_reset_token_hash
        UNIQUE (token_hash),

    CONSTRAINT fk_password_reset_token_user
        FOREIGN KEY (user_id)
        REFERENCES public.users(id)
        ON DELETE CASCADE
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX IF NOT EXISTS idx_password_reset_token_user
    ON public.password_reset_token(user_id);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_hash
    ON public.password_reset_token(token_hash);

CREATE INDEX IF NOT EXISTS idx_password_reset_token_expires
    ON public.password_reset_token(expires_at);


