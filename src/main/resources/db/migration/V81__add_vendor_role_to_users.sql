-- Add VENDOR role assignment for test users
-- This migration associates test users with the VENDOR role

DO $$
DECLARE
    vendor_role_id UUID;
    tenant_schema RECORD;
    user_record RECORD;
BEGIN
    -- Get the VENDOR role ID from public schema
    SELECT id INTO vendor_role_id FROM public.roles WHERE name = 'ROLE_VENDOR' LIMIT 1;
    
    IF vendor_role_id IS NOT NULL THEN
        -- For each tenant schema, create a vendor and assign the role
        FOR tenant_schema IN
            SELECT schema_name FROM public.tenants WHERE schema_name != 'public'
        LOOP
            -- Assign VENDOR role to users in this tenant
            EXECUTE format('
                UPDATE %I.users 
                SET role_id = %L 
                WHERE email LIKE ''%%@leadflow.dev''
                OR email LIKE ''%%@email.com''
                ',
                tenant_schema.schema_name,
                vendor_role_id
            );
        END LOOP;
    END IF;

    -- Also update users in public schema
    UPDATE public.users 
    SET role_id = vendor_role_id
    WHERE (email LIKE '%@leadflow.dev' OR email LIKE '%@email.com')
    AND role_id IS NULL;

END $$;
