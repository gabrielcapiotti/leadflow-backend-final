-- ============================================================
-- ADMIN USER SETUP - Execute this directly in PostgreSQL
-- ============================================================
-- Run this script with psql or another PostgreSQL client
-- It will create an admin user for testing

BEGIN;

-- 1. Get the ADMIN role ID from public schema
WITH admin_role AS (
    SELECT id FROM public.roles WHERE name = 'ROLE_ADMIN' LIMIT 1
)
-- 2. Insert test admin user
INSERT INTO public.users (
    id,
    name,
    email,
    password,
    role_id,
    failed_attempts,
    lock_until,
    credentials_updated_at,
    created_at,
    updated_at,
    deleted_at
)
SELECT
    '12345678-1234-1234-1234-123456789ABC'::uuid,
    'Admin Test',
    'admin@test.com',
    '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36jStoFm',  -- password: AdminPassword123!
    admin_role.id,
    0,
    NULL,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP,
    NULL
FROM admin_role
WHERE NOT EXISTS (
    SELECT 1 FROM public.users WHERE email = 'admin@test.com'
);

COMMIT;

-- Verify insertion
SELECT id, email, role_id FROM public.users WHERE email = 'admin@test.com';
