-- ============================================================================
-- V87: Add Tenant-Aware Indexes for Vendor Multi-Tenant Isolation
-- ============================================================================
-- OBJETIVO: Ensure performance and data integrity for multi-tenant vendor operations
-- ARQUITETURA: Schema-based multi-tenancy with (tenant_id, field) composite uniqueness
-- ============================================================================

-- ============================================================================
-- PRIMARY TENANT INDEX - Used by all queries
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_vendor_tenant_id
ON vendors(tenant_id);

-- ============================================================================
-- UNIQUE COMPOSITE INDEX - slug must be unique per tenant
-- ============================================================================
-- This replaces the old global unique constraint on slug

CREATE UNIQUE INDEX IF NOT EXISTS uq_vendor_slug_tenant_id
ON vendors(tenant_id, slug);

-- ============================================================================
-- COMPOSITE INDEX - user_email lookup by tenant
-- ============================================================================
-- Performance optimization for auth and vendor lookup by email

CREATE INDEX IF NOT EXISTS idx_vendor_tenant_email
ON vendors(tenant_id, user_email);

-- ============================================================================
-- COMPOSITE INDEX - external_customer_id lookup by tenant
-- ============================================================================
-- Performance optimization for Stripe/payment integrations

CREATE INDEX IF NOT EXISTS idx_vendor_tenant_external_customer
ON vendors(tenant_id, external_customer_id);

