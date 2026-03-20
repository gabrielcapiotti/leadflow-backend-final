/* ======================================================
   RENAME TEMPLATE_USERS TO USERS
   Align with User entity @Table(name = "users")
   ====================================================== */

ALTER TABLE IF EXISTS public.template_users RENAME TO users;

/* ======================================================
   ADD MISSING COLUMNS TO USERS
   ====================================================== */

ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS failed_attempts INTEGER NOT NULL DEFAULT 0;

ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS lock_until TIMESTAMPTZ;

ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS credentials_updated_at TIMESTAMPTZ;

ALTER TABLE public.users
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;


/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX IF NOT EXISTS idx_users_deleted_at
    ON public.users (deleted_at);

CREATE INDEX IF NOT EXISTS idx_users_lock_until
    ON public.users (lock_until);