/* ======================================================
   SAFE VERSION
   ====================================================== */

UPDATE public.users u
SET role_id = r.id,
    updated_at = CURRENT_TIMESTAMP
FROM public.roles r
WHERE r.name = 'ROLE_VENDOR'
  AND u.role_id IS NULL
  AND u.tenant_id = '00000000-0000-0000-0000-000000000000'
  AND (
        u.email LIKE '%@leadflow.dev'
     OR u.email LIKE '%@email.com'
  );