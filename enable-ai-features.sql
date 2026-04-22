-- Habilitar todas as features AI para o vendor do tenant de teste

-- Configurações do teste
-- Tenant: f80a9093-b505-406c-baf5-4b1f2f6d5fb9

-- 1. Descobrir o vendor_id para este tenant
-- Executar antes: SELECT id FROM public.vendors WHERE tenant_id = 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9';

-- 2. Com o vendor_id descoberto, executar o INSERT/UPDATE para cada feature

-- Assumindo que o vendor_id é o mesmo que o tenant_id (se houver apenas um vendor):
-- Vou inserir para todas as features AI

INSERT INTO public.vendor_features (id, tenant_id, vendor_id, feature_key, enabled, created_at, updated_at)
VALUES 
  (gen_random_uuid(), 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9', 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9', 'AI_CHAT', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (gen_random_uuid(), 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9', 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9', 'LEAD_SUMMARY', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (gen_random_uuid(), 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9', 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9', 'TITLE_SUGGESTION', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (gen_random_uuid(), 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9', 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9', 'REFINE_MESSAGE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (gen_random_uuid(), 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9', 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9', 'SENTIMENT_ANALYSIS', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (gen_random_uuid(), 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9', 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9', 'CLASSIFY_LEAD', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
  (gen_random_uuid(), 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9', 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9', 'GENERATE_RESPONSE', true, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
ON CONFLICT (vendor_id, feature_key, tenant_id) 
DO UPDATE SET enabled = true, updated_at = CURRENT_TIMESTAMP;

-- Verificar que as features foram habilitadas
SELECT feature_key, enabled FROM public.vendor_features 
WHERE tenant_id = 'f80a9093-b505-406c-baf5-4b1f2f6d5fb9'
ORDER BY feature_key;
