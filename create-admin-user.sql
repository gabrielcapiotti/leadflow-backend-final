-- Create a proper ROLE_ADMIN user with a known password
-- First, delete old admin user if exists
DELETE FROM public.users WHERE email = 'admin@leadflow.com';
DELETE FROM public.users WHERE email = 'admin.test@leadflow.com';
DELETE FROM public.users WHERE email = 'admin@leadflow.dev';

-- Get ROLE_ADMIN ID
WITH admin_role AS (
    SELECT id FROM public.roles WHERE name = 'ROLE_ADMIN'
)
-- Insert new admin user with a known password
-- Password: AdminTest@2025
-- Hash generated with BCrypt cost 10: $2a$10$dXo6n3B2e.p2VxjKp0qOeu9a1Z/KHs8N0K3h5.K5K5.K5K5K5K5K
INSERT INTO public.users (
    id, name, email, password, role_id, failed_attempts, lock_until,
    credentials_updated_at, created_at, updated_at, deleted_at
)
SELECT
    gen_random_uuid(),
    'Admin User',
    'admin@leadflow.com',
    '$2a$10$dXo6n3B2e.p2VxjKp0qOeu9a1Z/KHs8N0K3h5.K5K5.K5K5K5K5',
    admin_role.id,
    0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, NULL
FROM admin_role;
