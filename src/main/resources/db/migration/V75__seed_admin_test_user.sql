/* ======================================================
   V75__seed_admin_user.sql (DETERMINISTIC)
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

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
    name = 'Test Admin',
    email = 'admin.test@leadflow.com',
    updated_at = CURRENT_TIMESTAMP;