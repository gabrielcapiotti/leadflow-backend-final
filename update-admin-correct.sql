-- Procurar o hash correto do BCrypt para "Admin@123"
-- Usando bcrypt com cost 10 (como configurado no Spring Security)
-- Hash de "Admin@123":
UPDATE public.users 
SET password = '$2a$10$2TJ.OMXk7oKhVKLbw.rOU.b5vlNwPJPgdMjScfAZQCF8R7QVVCYIm'
WHERE email = 'admin@leadflow.com';

SELECT email, password FROM public.users WHERE email = 'admin@leadflow.com';
