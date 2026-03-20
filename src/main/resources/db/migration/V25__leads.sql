/* ======================================================
   RENAME TEMPLATE_LEADS TO LEADS
   Align with Lead entity @Table(name = "leads")
   ====================================================== */

ALTER TABLE IF EXISTS public.template_leads RENAME TO leads;

/* ======================================================
   ADD NEW COLUMNS
   ====================================================== */

ALTER TABLE public.leads
ADD COLUMN IF NOT EXISTS status VARCHAR(30);

ALTER TABLE public.leads
ADD COLUMN IF NOT EXISTS deleted_at TIMESTAMPTZ;


/* ======================================================
   DEFAULT VALUE
   ====================================================== */

ALTER TABLE public.leads
ALTER COLUMN status SET DEFAULT 'NEW';


/* ======================================================
   STATUS CONSTRAINT
   ====================================================== */

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


/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX IF NOT EXISTS idx_leads_status
    ON public.leads (status);

CREATE INDEX IF NOT EXISTS idx_leads_deleted_at
    ON public.leads (deleted_at);