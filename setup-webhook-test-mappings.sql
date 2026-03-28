-- ============================================================================
-- SETUP: Webhook Test Stripe Customer Mappings
-- ============================================================================
-- This script creates Stripe customer mappings for webhook tests
-- Run BEFORE executing test-webhooks-Oficial.ps1
-- ============================================================================

-- Step 1: Get a valid tenant UUID (assumes test_tenant exists)
-- If this query returns no results, you need to create a test tenant first
WITH test_tenant AS (
    SELECT id FROM tenants LIMIT 1
)
-- Step 2: Insert customer mappings for webhook tests
INSERT INTO stripe_customers (
    id, tenant_id, stripe_customer_id, status, created_at, updated_at, last_webhook_at
)
SELECT 
    gen_random_uuid(),
    t.id,
    customer_id,
    'active',
    NOW(),
    NOW(),
    NOW()
FROM test_tenant t
CROSS JOIN (
    VALUES 
        ('cus_invoice_paid_test'),
        ('cus_invoice_failed_test'),
        ('cus_subscription_deleted_test'),
        ('cus_checkout_session_test'),
        ('cus_invoice_paid_direct_test'),
        ('cus_idempotency_test')
) AS customers(customer_id)
ON CONFLICT (stripe_customer_id) DO UPDATE 
SET 
    status = 'active',
    updated_at = NOW()
WHERE stripe_customers.status != 'active';

-- Verify mappings were created
SELECT 
    stripe_customer_id,
    tenant_id,
    status,
    created_at
FROM stripe_customers
WHERE stripe_customer_id LIKE 'cus_%_test'
ORDER BY created_at DESC;
