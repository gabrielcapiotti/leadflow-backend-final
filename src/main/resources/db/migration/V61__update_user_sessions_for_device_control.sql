/* ======================================================
   USER SESSIONS - MULTI TENANT + DEVICE CONTROL
   ======================================================
   Note: Foreign key constraint already added in V48.
   This migration ensures all indexes are present.
   ====================================================== */

-- ======================================================
-- ENSURE ALL INDEXES EXIST
-- ======================================================

CREATE INDEX IF NOT EXISTS idx_session_user_tenant_active
ON public.user_sessions (user_id, tenant_id, active);

CREATE INDEX IF NOT EXISTS idx_session_token
ON public.user_sessions (token_id);