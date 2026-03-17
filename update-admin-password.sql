-- Atualizar senha do admin para "Admin@123456"
UPDATE public.users 
SET password = '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36jStoFm'
WHERE email = 'admin@leadflow.com';

-- Verificar
SELECT email, password FROM public.users WHERE email = 'admin@leadflow.com';
