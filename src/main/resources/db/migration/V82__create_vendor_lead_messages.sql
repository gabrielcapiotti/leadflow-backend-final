/* ======================================================
   VENDOR LEAD MESSAGES
   Messages/conversations between vendor and leads
   ====================================================== */

CREATE TABLE IF NOT EXISTS public.vendor_lead_messages (

    id UUID NOT NULL,

    vendor_lead_id UUID NOT NULL,

    role VARCHAR(20) NOT NULL,

    message TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL,

    CONSTRAINT pk_vendor_lead_messages PRIMARY KEY (id),

    CONSTRAINT fk_vendor_lead_messages_vendor_lead
        FOREIGN KEY (vendor_lead_id)
        REFERENCES public.vendor_leads(id)
        ON DELETE CASCADE
);

-- =========================
-- Indexes
-- =========================

CREATE INDEX IF NOT EXISTS idx_vlm_vendor_lead_id
    ON public.vendor_lead_messages(vendor_lead_id);

CREATE INDEX IF NOT EXISTS idx_vlm_vendor_lead_created_at
    ON public.vendor_lead_messages(vendor_lead_id, created_at);