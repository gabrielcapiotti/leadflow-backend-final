/* ======================================================
   PASSWORD RESET TOKEN SECURITY UPDATE
   Replaces plain token storage with hashed token
   ====================================================== */

-- Remove UNIQUE constraint if it exists
DO $$
BEGIN
    ALTER TABLE public.template_password_reset_tokens
        DROP CONSTRAINT IF EXISTS uq_template_password_reset_token;
EXCEPTION
    WHEN OTHERS THEN NULL;
END $$;

-- Remove plain token column if it exists
ALTER TABLE public.template_password_reset_tokens
    DROP COLUMN IF EXISTS token CASCADE;

-- Add hashed token column
ALTER TABLE public.template_password_reset_tokens
    ADD COLUMN IF NOT EXISTS token_hash VARCHAR(255) NOT NULL;

-- Add uniqueness constraint
DO $$
BEGIN
    ALTER TABLE public.template_password_reset_tokens
        ADD CONSTRAINT uq_template_password_reset_token_hash
            UNIQUE (token_hash);
EXCEPTION
    WHEN OTHERS THEN NULL;
END $$;

-- Index for fast lookup
CREATE INDEX IF NOT EXISTS idx_template_password_reset_token_hash
    ON public.template_password_reset_tokens (token_hash);