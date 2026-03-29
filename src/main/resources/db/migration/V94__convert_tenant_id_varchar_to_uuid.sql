-- V94: Convert tenant_id from VARCHAR to UUID (FINAL - ROBUST & SAFE)

CREATE EXTENSION IF NOT EXISTS pgcrypto;

DO $$
DECLARE
    rec RECORD;
BEGIN

    FOR rec IN 
        SELECT table_name
        FROM information_schema.columns
        WHERE column_name = 'tenant_id'
          AND table_schema = 'public'
    LOOP

        -- =====================================================
        -- DROP DEFAULT (SAFE - só se existir)
        -- =====================================================
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = rec.table_name
              AND column_name = 'tenant_id'
              AND column_default IS NOT NULL
        ) THEN
            EXECUTE format('
                ALTER TABLE public.%I
                ALTER COLUMN tenant_id DROP DEFAULT
            ', rec.table_name);
        END IF;

        -- =====================================================
        -- CONVERT TYPE (APENAS SE NÃO FOR UUID)
        -- =====================================================
        IF EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_name = rec.table_name
              AND column_name = 'tenant_id'
              AND data_type <> 'uuid'
        ) THEN

            EXECUTE format('
                ALTER TABLE public.%I
                ALTER COLUMN tenant_id TYPE UUID
                USING (
                    CASE 
                        WHEN tenant_id IS NULL THEN gen_random_uuid()
                        WHEN tenant_id::text ~* ''^[0-9a-f-]{36}$'' THEN tenant_id::text::uuid
                        ELSE gen_random_uuid()
                    END
                )
            ', rec.table_name);

        END IF;

    END LOOP;

END $$;

-- =====================================================
-- OPTIONAL HARDENING (EXECUTE MANUALMENTE DEPOIS)
-- =====================================================

-- DO $$
-- DECLARE rec RECORD;
-- BEGIN
--     FOR rec IN 
--         SELECT table_name
--         FROM information_schema.columns
--         WHERE column_name = 'tenant_id'
--           AND table_schema = 'public'
--     LOOP
--         EXECUTE format('
--             ALTER TABLE public.%I
--             ALTER COLUMN tenant_id SET NOT NULL
--         ', rec.table_name);
--     END LOOP;
-- END $$;

-- =====================================================
-- OPTIONAL DEFAULT (CASO SUA REGRA PERMITA)
-- =====================================================

-- ALTER TABLE public.users
-- ALTER COLUMN tenant_id SET DEFAULT gen_random_uuid();