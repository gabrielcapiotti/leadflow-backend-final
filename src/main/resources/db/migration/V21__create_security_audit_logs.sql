/* ======================================================
   V21__create_audit_logs.sql
   GLOBAL (PUBLIC SCHEMA)
   UNIFIED AUDIT SYSTEM
   ====================================================== */

CREATE TABLE public.audit_logs (

    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),

    /* ======================================================
       CLASSIFICAÇÃO DO EVENTO
       ====================================================== */

    event_category VARCHAR(50) NOT NULL CHECK (
        event_category IN ('SECURITY', 'SYSTEM', 'BUSINESS')
    ),

    action VARCHAR(100) NOT NULL,

    /* ======================================================
       CONTEXTO DO ATOR
       ====================================================== */

    actor_email VARCHAR(255),
    tenant_id UUID,

    /* ======================================================
       CONTEXTO DA ENTIDADE
       ====================================================== */

    entity_type VARCHAR(100) DEFAULT 'UNKNOWN',
    entity_id UUID,

    /* ======================================================
       SEGURANÇA (opcional)
       ====================================================== */

    success BOOLEAN,
    ip_address VARCHAR(100),
    user_agent VARCHAR(255),

    /* ======================================================
       OBSERVABILIDADE
       ====================================================== */

    correlation_id VARCHAR(100),

    /* ======================================================
       PAYLOAD / DETALHES
       ====================================================== */

    details JSONB,

    /* ======================================================
       AUDITORIA
       ====================================================== */

    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

/* ======================================================
   INDEXES
   ====================================================== */

CREATE INDEX idx_audit_logs_category
    ON public.audit_logs (event_category);

CREATE INDEX idx_audit_logs_action
    ON public.audit_logs (action);

CREATE INDEX idx_audit_logs_actor_email
    ON public.audit_logs (actor_email);

CREATE INDEX idx_audit_logs_tenant
    ON public.audit_logs (tenant_id);

CREATE INDEX idx_audit_logs_entity_type
    ON public.audit_logs (entity_type);

CREATE INDEX idx_audit_logs_entity_id
    ON public.audit_logs (entity_id);

CREATE INDEX idx_audit_logs_created_at
    ON public.audit_logs (created_at DESC);

CREATE INDEX idx_audit_logs_correlation
    ON public.audit_logs (correlation_id);