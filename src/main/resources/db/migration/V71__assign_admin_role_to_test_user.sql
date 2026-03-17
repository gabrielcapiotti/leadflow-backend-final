/* ======================================================
   ASSIGN ADMIN ROLE TO TEST USER
   ====================================================== */

DO $$
DECLARE
    admin_role_id UUID;
    test_user_id UUID;
BEGIN
    -- Get the test admin user ID
    SELECT u.id INTO test_user_id
    FROM public.users u
    WHERE u.email = 'admin.test@leadflow.com'
    LIMIT 1;
    
    -- Get the ADMIN role ID
    SELECT r.id INTO admin_role_id
    FROM public.roles r
    WHERE r.name = 'ROLE_ADMIN'
    LIMIT 1;
    
    -- Update user with ADMIN role
    IF test_user_id IS NOT NULL AND admin_role_id IS NOT NULL THEN
        UPDATE public.users 
        SET role_id = admin_role_id,
            updated_at = CURRENT_TIMESTAMP
        WHERE id = test_user_id;
        
        RAISE NOTICE 'Admin role assigned to test user';
    ELSE
        RAISE WARNING 'Could not find test user or admin role';
    END IF;
END $$;
