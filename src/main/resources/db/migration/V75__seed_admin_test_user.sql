/* ======================================================
   V75__seed_admin_test_user.sql (DETERMINISTIC)
   GLOBAL (PUBLIC SCHEMA)

   Responsabilidades:
   - Garantir existência de tenant padrão
   - Criar/atualizar admin de teste de forma consistente
   ====================================================== */

-- ======================================================
-- 1. GARANTIR TENANT PADRÃO
-- ======================================================

INSERT INTO public.tenants (id, name, status, created_at, updated_at)
VALUES (
    '00000000-0000-0000-0000-000000000000'::uuid,
    'Default Tenant',
    'ACTIVE',
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
)
ON CONFLICT (id) DO NOTHING;

-- ======================================================
-- 2. VALIDAR ROLE ADMIN (FAIL FAST)
-- ======================================================

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM public.roles WHERE name = 'ROLE_ADMIN'
    ) THEN
        RAISE EXCEPTION 'ROLE_ADMIN not found. Seed roles before V75.';
    END IF;
END;
$$;

-- ======================================================
-- 3. UPSERT ADMIN USER
-- ======================================================

INSERT INTO public.users (
    id,
    name,
    email,
    password_hash,
    role_id,
    tenant_id,
    failed_attempts,
    lock_until,
    credentials_updated_at,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    '550e8400-e29b-41d4-a716-446655440000'::uuid,
    'Test Admin',
    'admin.test@leadflow.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36jStoFm',
    r.id,
    '00000000-0000-0000-0000-000000000000'::uuid,
    0,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL
FROM public.roles r
WHERE r.name = 'ROLE_ADMIN'
ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    email = EXCLUDED.email,
    password_hash = EXCLUDED.password_hash,
    role_id = EXCLUDED.role_id,
    tenant_id = EXCLUDED.tenant_id,
    updated_at = CURRENT_TIMESTAMP;