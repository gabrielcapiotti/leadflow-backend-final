-- V4__bootstrap_default_tenant.sql (DETERMINISTIC)
-- Registra o tenant "default" na tabela public.tenants
-- Deve ser executado após a criação do schema (V1)

-- Falha rápido se schema não existe
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.schemata 
        WHERE schema_name = 'default'
    ) THEN
        RAISE EXCEPTION 'Schema "default" não existe. Verifique se V1 foi executada corretamente.';
    END IF;
END $$;

-- Insert determinístico com conflict resolution
INSERT INTO public.tenants (
    id,
    schema_name,
    name,
    status,
    created_at,
    updated_at
)
VALUES (
    '00000000-0000-0000-0000-000000000000'::uuid,
    'default',
    'Default Tenant',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO UPDATE SET 
    schema_name = 'default',
    name = 'Default Tenant',
    status = 'ACTIVE',
    updated_at = CURRENT_TIMESTAMP;