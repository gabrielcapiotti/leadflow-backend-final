-- V5__setup_default_tenant_permissions.sql (DETERMINISTIC)
-- Concede permissões ao schema "default"
-- Falha rápido se pre-requisitos não foram atendidos

-- Validator: Falha se schema não existe
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 
        FROM information_schema.schemata 
        WHERE schema_name = 'default'
    ) THEN
        RAISE EXCEPTION 'Schema "default" não existe. Verifique se V1 foi executada corretamente.';
    END IF;

    IF NOT EXISTS (
        SELECT 1 
        FROM pg_roles 
        WHERE rolname = 'app_user'
    ) THEN
        RAISE EXCEPTION 'Role "app_user" não existe. Deve ser criado antes das migrations.';
    END IF;
END $$;

-- Permissões no schema
GRANT USAGE ON SCHEMA "default" TO app_user;
GRANT CREATE ON SCHEMA "default" TO app_user;

-- Permissões em objetos já existentes
GRANT ALL ON ALL TABLES IN SCHEMA "default" TO app_user;
GRANT ALL ON ALL SEQUENCES IN SCHEMA "default" TO app_user;
GRANT ALL ON ALL FUNCTIONS IN SCHEMA "default" TO app_user;

-- Permissões padrão para objetos futuros
ALTER DEFAULT PRIVILEGES IN SCHEMA "default" GRANT ALL ON TABLES TO app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA "default" GRANT ALL ON SEQUENCES TO app_user;
ALTER DEFAULT PRIVILEGES IN SCHEMA "default" GRANT ALL ON FUNCTIONS TO app_user;