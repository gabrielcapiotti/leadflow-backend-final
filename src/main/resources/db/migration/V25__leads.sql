/* ======================================================
   LEADS TABLE (FINAL STRUCTURE - NO TEMPLATE)
   ====================================================== */

CREATE TABLE IF NOT EXISTS public.leads (

    id UUID NOT NULL,

    user_id UUID,

    name VARCHAR(255),
    email VARCHAR(255),
    phone VARCHAR(50),

    status VARCHAR(30) DEFAULT 'NEW',

    created_at TIMESTAMPTZ,
    updated_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ
);

-- PRIMARY KEY (idempotente)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'pk_leads'
    ) THEN
        ALTER TABLE public.leads
        ADD CONSTRAINT pk_leads PRIMARY KEY (id);
    END IF;
END $$;


-- STATUS DEFAULT (garantia)
ALTER TABLE public.leads
ALTER COLUMN status SET DEFAULT 'NEW';


-- STATUS CHECK (idempotente e seguro)
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1
        FROM pg_constraint
        WHERE conname = 'chk_lead_status'
    ) THEN
        ALTER TABLE public.leads
        ADD CONSTRAINT chk_lead_status
        CHECK (status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'CLOSED'));
    END IF;
END $$;


-- INDEXES
CREATE INDEX IF NOT EXISTS idx_leads_status
    ON public.leads (status);

CREATE INDEX IF NOT EXISTS idx_leads_deleted_at
    ON public.leads (deleted_at);

CREATE INDEX IF NOT EXISTS idx_leads_user_id
    ON public.leads (user_id);