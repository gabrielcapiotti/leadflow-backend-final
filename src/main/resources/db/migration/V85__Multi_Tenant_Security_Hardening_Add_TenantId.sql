-- ============================================================================
-- V85: Multi-Tenant Security Hardening - Add tenant_id to critical entities
-- ============================================================================
-- OBJETIVO: Garantir que TODAS as entidades multi-tenant tenham tenant_id
-- E que filters Hibernate possam ser aplicados globalmente
-- 
-- 🔑 ARQUITETURA: Schema-based tenancy (STRING tenant identifiers)
-- tenant_id = "public", "tenant_a", etc. (NOT UUID foreign keys)
-- ============================================================================

-- 1. VendorLead - CRÍTICA
ALTER TABLE vendor_leads 
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'public';

CREATE INDEX IF NOT EXISTS idx_vendor_leads_tenant_id 
ON vendor_leads(tenant_id);

-- 2. Vendor - CRÍTICA
ALTER TABLE vendors
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'public';

CREATE INDEX IF NOT EXISTS idx_vendors_tenant_id 
ON vendors(tenant_id);

-- 3. UserSession - CRÍTICA (para isolamento de sessões)
ALTER TABLE user_sessions
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'public';

CREATE INDEX IF NOT EXISTS idx_user_sessions_tenant_id 
ON user_sessions(tenant_id);

-- 4. Payment - IMPORTANTE (dados financeiros críticos)
ALTER TABLE payments
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'public';

CREATE INDEX IF NOT EXISTS idx_payments_tenant_id 
ON payments(tenant_id);

-- 5. Setting - Configurações por tenant
ALTER TABLE settings
ADD COLUMN IF NOT EXISTS tenant_id VARCHAR(63) NOT NULL DEFAULT 'public';

CREATE INDEX IF NOT EXISTS idx_settings_tenant_id 
ON settings(tenant_id);

-- ============================================================================
-- ÍNDICES COMPOSTOS PARA PERFORMANCE
-- ============================================================================

-- VendorLead: queries por tenant + status
CREATE INDEX IF NOT EXISTS idx_vendor_leads_tenant_status 
ON vendor_leads(tenant_id, status);

-- Vendor: queries por tenant
CREATE INDEX IF NOT EXISTS idx_vendors_tenant_created 
ON vendors(tenant_id, created_at DESC);

-- Payment: queries por tenant + status
CREATE INDEX IF NOT EXISTS idx_payments_tenant_status 
ON payments(tenant_id, status);

-- ============================================================================
-- ✅ OBSERVAÇÕES IMPORTANTES
-- ============================================================================
-- 1. NÃO adicionamos FOREIGN KEY pois usamos schema-based tenancy
-- 2. tenant_id é identificador lógico (STRING), não UUID
-- 3. Hibernat@Filter e TenantContext garantem isolamento (aplicação)
-- 4. Índices garantem performance (banco)
-- 5. Constraints são aplicadas via SQL DEFAULT + Hibernate @Filter

COMMIT;
