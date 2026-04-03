-- Create admin user for testing
-- Password is: AdminTest@123
-- Using bcrypt hash

INSERT INTO public.users (id, name, email, password, role_id, tenant_id, created_at, updated_at)
VALUES (
    gen_random_uuid(),
    'Admin TestUser',
    'admin.test@leadflow.com',
    '$2a$10$XK2cH5zWMBm1CJ/r9cU2Je8jLLBCHQ3C2V7ld.yKYNlH4hXIGZB9K',
    '00000000-0000-0000-0000-000000000002',
    'a7f39d85-d031-4a29-ad7f-951a1774f903',
    NOW(),
    NOW()
)
ON CONFLICT (email, tenant_id) DO NOTHING;

-- Verify user was created
SELECT id, name, email, role_id FROM public.users WHERE email = 'admin.test@leadflow.com' LIMIT 1;
