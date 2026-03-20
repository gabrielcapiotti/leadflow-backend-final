/* ======================================================
   VENDOR LEAD CONVERSATIONS
   Stores messages exchanged with a lead
   ====================================================== */

CREATE TABLE IF NOT EXISTS public.vendor_lead_conversations (

    id UUID PRIMARY KEY,

    vendor_lead_id UUID NOT NULL,

    lead_id UUID,

    role VARCHAR(20) NOT NULL,

    content TEXT NOT NULL,

    sender VARCHAR(50),

    tenant VARCHAR(100),

    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_vendor_lead_conversations_lead
        FOREIGN KEY (vendor_lead_id)
        REFERENCES public.vendor_leads(id)
        ON DELETE CASCADE,

    CONSTRAINT chk_vendor_lead_conversation_role
        CHECK (role IN ('USER','ASSISTANT','SYSTEM'))
);


/* ======================================================
   INDEXES
   ====================================================== */

-- Conversation timeline per lead
CREATE INDEX IF NOT EXISTS idx_vendor_lead_conversations_lead_created
    ON public.vendor_lead_conversations (vendor_lead_id, created_at DESC);

-- Filtering by role
CREATE INDEX IF NOT EXISTS idx_vendor_lead_conversations_role
    ON public.vendor_lead_conversations (role);

-- Fast lookup by lead only
CREATE INDEX IF NOT EXISTS idx_vendor_lead_conversations_lead
    ON public.vendor_lead_conversations (vendor_lead_id);

-- Lookup by lead_id (entity mapping)
CREATE INDEX IF NOT EXISTS idx_vendor_lead_conversations_lead_id
    ON public.vendor_lead_conversations (lead_id);

-- Filtering by sender
CREATE INDEX IF NOT EXISTS idx_vendor_lead_conversations_sender
    ON public.vendor_lead_conversations (sender);

-- Filtering by tenant
CREATE INDEX IF NOT EXISTS idx_vendor_lead_conversations_tenant
    ON public.vendor_lead_conversations (tenant);

-- Index for lead_id lookups
CREATE INDEX IF NOT EXISTS idx_vendor_lead_conversations_lead_id
    ON public.vendor_lead_conversations (lead_id);

-- Index for tenant filtering
CREATE INDEX IF NOT EXISTS idx_vendor_lead_conversations_tenant
    ON public.vendor_lead_conversations (tenant);

-- Composite index for sender + role queries
CREATE INDEX IF NOT EXISTS idx_vendor_lead_conversations_sender_role
    ON public.vendor_lead_conversations (sender, role);