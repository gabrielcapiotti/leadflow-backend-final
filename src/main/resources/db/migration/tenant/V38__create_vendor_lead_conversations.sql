/* ======================================================
   V38__create_vendor_lead_conversations.sql
   TENANT SCHEMA
   ====================================================== */

CREATE TABLE vendor_lead_conversations (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    vendor_lead_id UUID NOT NULL,

    role VARCHAR(20) NOT NULL
        CHECK (role IN ('USER','ASSISTANT','SYSTEM')),

    content TEXT NOT NULL,

    sender VARCHAR(50),

    -- opcional: metadata para IA / contexto
    metadata JSONB,

    -- opcional: rastreamento de request
    correlation_id UUID,

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT fk_vendor_lead_conversations_lead
        FOREIGN KEY (vendor_lead_id)
        REFERENCES vendor_leads(id)
        ON DELETE CASCADE
);

/* ======================================================
   INDEXES
   ====================================================== */

-- timeline principal (chat)
CREATE INDEX idx_vlc_lead_created
    ON vendor_lead_conversations (vendor_lead_id, created_at DESC);

-- filtro por role (analytics / IA)
CREATE INDEX idx_vlc_role
    ON vendor_lead_conversations (role);

-- busca por sender (se usado no sistema)
CREATE INDEX idx_vlc_sender
    ON vendor_lead_conversations (sender);

-- rastreamento de requisições (debug / tracing)
CREATE INDEX idx_vlc_correlation
    ON vendor_lead_conversations (correlation_id);
