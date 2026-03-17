-- Create admin user with valid email domain for testing
WITH admin_role AS (
    SELECT id FROM public.roles WHERE name = 'ROLE_ADMIN' LIMIT 1
)
INSERT INTO public.users (
    id, name, email, password, role_id, failed_attempts, lock_until,
    credentials_updated_at, created_at, updated_at, deleted_at
)
SELECT
    '99999999-9999-9999-9999-999999999999'::uuid,
    'Admin Test User',
    'admin@leadflow.dev',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36jStoFm',  -- password: AdminPassword123!
    admin_role.id,
    0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL
FROM admin_role
WHERE NOT EXISTS (SELECT 1 FROM public.users WHERE email = 'admin@leadflow.dev');
