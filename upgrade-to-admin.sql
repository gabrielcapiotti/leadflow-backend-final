-- Update user to ADMIN (última criação)
UPDATE public.users 
SET role_id = '00000000-0000-0000-0000-000000000002'
WHERE id = '3cf0d074-ec2b-44f0-88fe-8a13d90a82b3';

-- Verify
SELECT id, email, role_id FROM public.users WHERE id = '3cf0d074-ec2b-44f0-88fe-8a13d90a82b3';
