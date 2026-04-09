/* ======================================================
   V69__normalize_vendor_leads_stage.sql
   TENANT SCHEMA
   ====================================================== */

-- =========================
-- NORMALIZAÇÃO DE DADOS
-- =========================

UPDATE vendor_leads
SET stage = CASE LOWER(stage)
    WHEN 'novo' THEN 'NEW'
    WHEN 'contato' THEN 'CONTACT'
    WHEN 'proposta' THEN 'PROPOSAL'
    WHEN 'fechado' THEN 'CLOSED'
    WHEN 'perdido' THEN 'LOST'
    ELSE stage
END;

ALTER TABLE vendor_leads
DROP CONSTRAINT IF EXISTS chk_vendor_leads_stage;

ALTER TABLE vendor_leads
ADD CONSTRAINT chk_vendor_leads_stage
CHECK (
    stage IN (
        'NEW',
        'CONTACT',
        'PROPOSAL',
        'NEGOTIATION',
        'CLOSED',
        'LOST'
    )
);