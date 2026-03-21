/* ======================================================
   USERS TABLE (FINAL STRUCTURE - NO TEMPLATE)
   ====================================================== */

CREATE TABLE IF NOT EXISTS public.users (

    id UUID NOT NULL,
    name VARCHAR(255),
    email VARCHAR(255),
    password VARCHAR(255),

    role_id UUID,

    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,

    failed_attempts INTEGER NOT NULL DEFAULT 0,
    lock_until TIMESTAMPTZ,
    credentials_updated_at TIMESTAMPTZ
);

-- PRIMARY KEY (idempotente)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'pk_users'
    ) THEN
        ALTER TABLE public.users
        ADD CONSTRAINT pk_users PRIMARY KEY (id);
    END IF;
END $$;

-- UNIQUE EMAIL (recomendado)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_users_email'
    ) THEN
        ALTER TABLE public.users
        ADD CONSTRAINT uq_users_email UNIQUE (email);
    END IF;
END $$;

-- INDEXES
CREATE INDEX IF NOT EXISTS idx_users_deleted_at
    ON public.users (deleted_at);

CREATE INDEX IF NOT EXISTS idx_users_lock_until
    ON public.users (lock_until);

CREATE INDEX IF NOT EXISTS idx_users_email
    ON public.users (email);