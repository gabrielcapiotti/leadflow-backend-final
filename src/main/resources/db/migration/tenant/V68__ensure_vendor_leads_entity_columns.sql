/* ======================================================
   V68__align_vendor_leads_entity.sql (DETERMINISTIC)
   TENANT SCHEMA
   ====================================================== */

-- =========================
-- COLUMNS (Determinístico)
-- =========================

ALTER TABLE vendor_leads 
ADD COLUMN IF NOT EXISTS created_date TIMESTAMPTZ;

ALTER TABLE vendor_leads 
ADD COLUMN IF NOT EXISTS status VARCHAR(50);

ALTER TABLE vendor_leads 
ADD COLUMN IF NOT EXISTS owner_email VARCHAR(255);

ALTER TABLE vendor_leads 
ADD COLUMN IF NOT EXISTS resumo_estrategico TEXT;

ALTER TABLE vendor_leads 
ADD COLUMN IF NOT EXISTS score INTEGER;

-- =========================
-- DATA MIGRATION (Seguro)
-- =========================

UPDATE vendor_leads
SET created_date = created_at
WHERE created_date IS NULL
  AND created_at IS NOT NULL;

-- =========================
-- DATA DEFAULTS (Seguro)
-- =========================

UPDATE vendor_leads
SET status = 'NEW'
WHERE status IS NULL;

UPDATE vendor_leads
SET score = 0
WHERE score IS NULL;

-- =========================
-- CONSTRAINT (Determinístico)
-- =========================

ALTER TABLE vendor_leads
ADD CONSTRAINT chk_vendor_leads_status
CHECK (status IN ('NEW','CONTACT','PROPOSAL','NEGOTIATION','CLOSED','LOST'));

-- =========================
-- DEFAULT
-- =========================

ALTER TABLE vendor_leads
ALTER COLUMN created_date SET DEFAULT CURRENT_TIMESTAMP;

-- =========================
-- INDEX
-- =========================

CREATE INDEX IF NOT EXISTS idx_vendor_leads_created_date
ON vendor_leads (created_date);