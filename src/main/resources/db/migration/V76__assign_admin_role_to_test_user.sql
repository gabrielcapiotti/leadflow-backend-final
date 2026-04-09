/* ======================================================
   V76__assign_admin_role.sql
   GLOBAL (PUBLIC SCHEMA)
   ====================================================== */

UPDATE public.users u
SET role_id = r.id,
    updated_at = CURRENT_TIMESTAMP
FROM public.roles r
WHERE r.name = 'ROLE_ADMIN'
  AND u.email = 'admin.test@leadflow.com'
  AND u.tenant_id = '00000000-0000-0000-0000-000000000000';