/* ======================================================
   V18__migrate_legacy_subscription_status.sql
   TENANT SCHEMA
   
   Data migration only - converts legacy Portuguese status
   to English standard. Columns already created in V8.
   
   Idempotent: IF EXISTS checks prevent errors on new databases.
   ====================================================== */

DO $$
BEGIN
    -- Migrate legacy Portuguese status to new English standard
    -- This runs only if the old column exists (legacy database)
    IF EXISTS (
        SELECT 1
        FROM information_schema.columns
        WHERE table_schema = current_schema()
          AND table_name = 'vendors'
          AND column_name = 'status_assinatura'
    ) THEN

        -- Convert Portuguese status to English
        UPDATE vendors
        SET subscription_status = CASE LOWER(COALESCE(status_assinatura, 'trial'))
            WHEN 'ativa' THEN 'ACTIVE'
            WHEN 'inadimplente' THEN 'PAST_DUE'
            WHEN 'suspensa' THEN 'CANCELED'
            WHEN 'cancelada' THEN 'CANCELED'
            WHEN 'expirada' THEN 'EXPIRED'
            WHEN 'inativa' THEN 'CANCELED'
            WHEN 'trial' THEN 'TRIAL'
            WHEN 'trialing' THEN 'TRIALING'
            ELSE 'TRIAL'
        END
        WHERE subscription_status IS NULL
           OR subscription_status NOT IN ('TRIAL', 'TRIALING', 'ACTIVE', 'PAST_DUE', 'CANCELED', 'EXPIRED');

        -- Drop legacy column
        ALTER TABLE vendors DROP COLUMN status_assinatura;

    END IF;
END $$;