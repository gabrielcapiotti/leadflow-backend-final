-- ============================================================
-- FIX: Remove UNIQUE constraint on vendor.name
-- ============================================================
-- 
-- 🔴 PROBLEMA CRÍTICO IDENTIFICADO:
--    A constraint uq_vendors_name causa violações porque:
--    1. vendor.name NÃO é um identificador único
--    2. Múltiplos usuários com mesmo name geram colisão
--    3. Causa HTTP 500 "DataIntegrityViolationException" em login
--
-- ✅ SOLUÇÃO:
--    Remover constraint de UNIQUE em name
--    Usar email como identificador (já tem UNIQUE implícita via índice)
--    Usar slug para URLs (já é único com UUID suffix)
--
-- ============================================================

-- Remover constraint (select first the schema you're using, default is 'public')
ALTER TABLE vendors DROP CONSTRAINT uq_vendors_name;

-- Verificar se constraint foi removida
SELECT constraint_name 
FROM information_schema.table_constraints 
WHERE table_name = 'vendors' 
AND constraint_type = 'UNIQUE';

-- Resultado esperado: sem linha de "uq_vendors_name"

-- ============================================================
-- VERIFICAÇÃO:
-- ============================================================
-- Após remover, testar multitenancy com:
-- 1. CREATE user1@tenant_a.com
-- 2. CREATE user2@tenant_a.com com mesmo name → DEVE SUCEDER
-- 3. Vendor 1 e 2 devem ter names ÚNICOS com suffix UUID
-- ============================================================
