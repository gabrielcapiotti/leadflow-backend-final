/* ======================================================
   LEAD STATUS HISTORY
   Audit trail of lead status changes
   ====================================================== */

CREATE TABLE IF NOT EXISTS public.lead_status_history (

    id UUID NOT NULL,

    lead_id UUID NOT NULL,

    status VARCHAR(30) NOT NULL,

    changed_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    changed_by UUID,

    CONSTRAINT pk_lead_status_history PRIMARY KEY (id),

    CONSTRAINT fk_lead_status_history_lead
        FOREIGN KEY (lead_id)
        REFERENCES public.leads(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_lead_status_history_status
        CHECK (status IN ('NEW', 'CONTACTED', 'QUALIFIED', 'CLOSED'))
);


/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX IF NOT EXISTS idx_lsh_lead_id
    ON public.lead_status_history (lead_id);

CREATE INDEX IF NOT EXISTS idx_lsh_lead_changed_at
    ON public.lead_status_history (lead_id, changed_at DESC);

CREATE INDEX IF NOT EXISTS idx_lsh_status
    ON public.lead_status_history (status);

CREATE INDEX IF NOT EXISTS idx_lsh_changed_by
    ON public.lead_status_history (changed_by);