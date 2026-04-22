/* ======================================================
   V82__create_vendor_lead_messages.sql (DETERMINISTIC)
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

CREATE TABLE public.vendor_lead_messages (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    vendor_lead_id UUID NOT NULL,

    tenant_id UUID NOT NULL,

    role VARCHAR(20) NOT NULL,

    message TEXT NOT NULL,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_vendor_lead_messages_vendor_lead
        FOREIGN KEY (vendor_lead_id)
        REFERENCES public.vendor_leads(id)
        ON DELETE CASCADE,

    CONSTRAINT fk_vendor_lead_messages_tenant
        FOREIGN KEY (tenant_id)
        REFERENCES public.tenants(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_vendor_lead_messages_role
        CHECK (role IN ('USER','ASSISTANT','SYSTEM'))
);

-- INDEXES

CREATE INDEX idx_vlm_lead_created
    ON public.vendor_lead_messages (vendor_lead_id, created_at DESC);

CREATE INDEX idx_vlm_tenant_id
    ON public.vendor_lead_messages (tenant_id);

CREATE INDEX idx_vlm_role
    ON public.vendor_lead_messages (role);