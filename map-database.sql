-- =============================================================
-- LEADFLOW DATABASE SCHEMA MAPPING
-- =============================================================

-- List all tables
SELECT 
    table_schema,
    table_name,
    string_agg(column_name || ' (' || udt_name || ')', ', ') as columns
FROM information_schema.columns
WHERE table_schema = 'public'
GROUP BY table_schema, table_name
ORDER BY table_name;

-- =============================================================
-- For each table: columns with constraints
-- =============================================================

-- USERS table
\d public.users

-- ROLES table
\d public.roles

-- LEADS table
\d public.leads

-- LEAD_STATUS_HISTORY table
\d public.lead_status_history

-- VENDORS table
\d public.vendors

-- VENDOR_LEADS table
\d public.vendor_leads

-- SESSIONS table
\d public.sessions

-- REFRESH_TOKENS table
\d public.refresh_tokens

-- LOGIN_AUDIT table
\d public.login_audit

-- SECURITY_AUDIT table
\d public.security_audit

-- WEBHOOK_EVENTS table
\d public.webhook_events

-- STRIPE_WEBHOOKS table
\d public.stripe_webhooks

-- PAYMENTS table
\d public.payments

-- BILLING_PLANS table
\d public.billing_plans

-- =============================================================
-- Foreign Key Relationships
-- =============================================================

SELECT
    tc.table_name,
    kcu.column_name,
    ccu.table_name AS foreign_table,
    ccu.column_name AS foreign_column
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
    ON tc.constraint_name = kcu.constraint_name
    AND tc.table_schema = kcu.table_schema
JOIN information_schema.constraint_column_usage AS ccu
    ON ccu.constraint_name = tc.constraint_name
    AND ccu.table_schema = tc.table_schema
WHERE tc.constraint_type = 'FOREIGN KEY'
ORDER BY tc.table_name;

-- =============================================================
-- Indexes
-- =============================================================

SELECT
    schemaname,
    tablename,
    indexname,
    indexdef
FROM pg_indexes
WHERE schemaname = 'public'
ORDER BY tablename, indexname;
