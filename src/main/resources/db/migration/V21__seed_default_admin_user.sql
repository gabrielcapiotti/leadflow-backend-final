/* ======================================================
   DEFAULT ADMIN USER SEED (FOR EACH TENANT)
   
   SAFE VERSION: Only seeds users if schema and table exist
   ====================================================== */

DO $$
DECLARE
    tenant_schema RECORD;
    schema_exists BOOLEAN;
    table_exists BOOLEAN;
BEGIN
    FOR tenant_schema IN
        SELECT schema_name FROM public.tenants
    LOOP
        -- Check if schema exists
        SELECT EXISTS (
            SELECT 1 FROM information_schema.schemata 
            WHERE schema_name = tenant_schema.schema_name
        ) INTO schema_exists;
        
        -- Check if users table exists in schema
        SELECT EXISTS (
            SELECT 1 FROM information_schema.tables 
            WHERE table_schema = tenant_schema.schema_name 
            AND table_name = 'users'
        ) INTO table_exists;
        
        -- Only insert if both schema and table exist
        IF schema_exists AND table_exists THEN
            EXECUTE format(
                '
                INSERT INTO %I.users (
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
                    ''00000000-0000-0000-0000-00000000A001''::uuid,
                    ''Administrador'',
                    ''admin@leadflow.local'',
                    ''$2a$10$eORKR/wJbinHFh6u/KKta.3HX.tTl.GdmktKEVWtScZ2g/4YyAMiW'',
                    r.id,
                    0,
                    NULL,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP,
                    NULL
                FROM %I.roles r
                WHERE r.name = ''ROLE_ADMIN''
                AND NOT EXISTS (
                    SELECT 1
                    FROM %I.users u
                    WHERE u.email = ''admin@leadflow.local''
                )
                ',
                tenant_schema.schema_name,
                tenant_schema.schema_name,
                tenant_schema.schema_name
            );
        ELSE
            RAISE NOTICE 'Skipping user seed for schema % (exists: %, table exists: %)', 
                tenant_schema.schema_name, schema_exists, table_exists;
        END IF;
    END LOOP;
END $$;