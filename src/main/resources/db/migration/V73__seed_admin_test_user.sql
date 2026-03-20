/* ======================================================
   SEED ADMIN USER FOR TESTING (PUBLIC TENANT)
   ====================================================== */

DO $$
DECLARE
    admin_role_id UUID;
    tenant_schema varchar;
    users_table_exists BOOLEAN;
BEGIN
    -- Get the public tenant schema
    tenant_schema := 'public';
    
    -- Check if users table exists in public schema
    SELECT EXISTS (
        SELECT 1 FROM information_schema.tables 
        WHERE table_schema = 'public' 
        AND table_name = 'users'
    ) INTO users_table_exists;
    
    -- Get the ADMIN role ID from the public schema
    SELECT r.id INTO admin_role_id
    FROM public.roles r
    WHERE r.name = 'ROLE_ADMIN'
    LIMIT 1;
    
    -- Insert admin user if doesn't exist (and table exists)
    IF users_table_exists AND admin_role_id IS NOT NULL THEN
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
            '550e8400-e29b-41d4-a716-446655440000'::uuid,
            'Test Admin',
            'admin.test@leadflow.com',
            '$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcg7b3XeKeUxWdeS86E36jStoFm',  -- password: 'AdminPassword123!'
            admin_role_id,
            0,
            NULL,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP,
            NULL
        WHERE NOT EXISTS (
            SELECT 1 FROM public.users u
            WHERE u.email = 'admin.test@leadflow.com'
        );
        
        RAISE NOTICE 'Admin user seeded successfully';
    ELSE
        IF NOT users_table_exists THEN
            RAISE NOTICE 'Skipping admin user seed: users table does not exist in public schema';
        ELSIF admin_role_id IS NULL THEN
            RAISE WARNING 'ROLE_ADMIN not found in public schema';
        END IF;
    END IF;
END $$;
