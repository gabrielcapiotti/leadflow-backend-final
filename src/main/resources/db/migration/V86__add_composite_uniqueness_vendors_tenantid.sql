-- ============================================================================
-- V86: Add Composite Uniqueness Constraint for Vendor slug + tenant_id
-- ============================================================================
-- OBJETIVO: Enforce uniqueness of vendor slug within tenant context
-- Garantir que cada tenant pode ter apenas um vendor com determinado slug
-- 
-- 🔒 SEGURANÇA: Impede colisões de slug entre tenants e garante isolamento
-- ============================================================================

-- Remove existing unique constraint on slug if it exists (allowing per-tenant uniqueness)
ALTER TABLE vendors 
DROP CONSTRAINT IF EXISTS uk_vendors_slug;

-- Add composite unique constraint on (slug, tenant_id)
ALTER TABLE vendors
ADD CONSTRAINT uk_vendors_slug_tenant_id 
UNIQUE (slug, tenant_id);

-- Create index for efficient lookups by slug and tenant_id
CREATE INDEX IF NOT EXISTS idx_vendors_slug_tenant_id
ON vendors(slug, tenant_id);
