/* ======================================================
   FIX EMAIL UNIQUE CONSTRAINT FOR MULTI-TENANT
   
   PROBLEM: 
   - Previous UNIQUE(email) was global (no tenant awareness)
   - Prevents same email in different tenants
   - Violates multi-tenant architecture
   
   SOLUTION:
   - Drop old UNIQUE(email) constraint
   - Add new UNIQUE(email, tenant_id) constraint
   - Allows same email per tenant (multi-tenant compliant)
   ====================================================== */

-- Drop old global UNIQUE constraint (if exists)
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_users_email'
        AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE public.users
        DROP CONSTRAINT uq_users_email;
    END IF;
END $$;

-- Add new tenant-aware UNIQUE constraint
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'uq_users_email_tenant_id'
        AND conrelid = 'public.users'::regclass
    ) THEN
        ALTER TABLE public.users
        ADD CONSTRAINT uq_users_email_tenant_id UNIQUE (email, tenant_id);
    END IF;
END $$;

-- Create indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_users_email_tenant_id 
    ON public.users (email, tenant_id);

CREATE INDEX IF NOT EXISTS idx_users_email 
    ON public.users (email);
