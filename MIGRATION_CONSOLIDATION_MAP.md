# Migration Consolidation Strategy

## Current State: 68 migrations spread across multiple domains

---

## PROPOSED CONSOLIDATION MAP

### GROUP 1: Core Infrastructure (3 → 1 migration)
**New file**: `V0__core_infrastructure.sql`
- V0__extensions.sql → CREATE EXTENSION pgcrypto
- V1__create_tenants.sql → CREATE TABLE public.tenants + indexes
- V2__create_roles.sql → CREATE TABLE roles in all tenant schemas

### GROUP 2: Base Templates & Global Tables (2 → 1 migration)
**New file**: `V1__global_and_template_tables.sql`
- V5__public_tables.sql → CREATE public.template_roles, public.template_users, public.roles, public.tenants (duplicate of V1?)
- V19__settings.sql → CREATE public.template_settings

### GROUP 3: Seed Core Roles (2 → 1 migration)
**New file**: `V2__seed_core_roles.sql`
- V6__seed_roles.sql → INSERT INTO roles (ROLE_USER, ROLE_ADMIN) for public tenant + all tenant schemas
- V37__seed_roles_for_tenants.sql → INSERT INTO roles for tenants (likely same as V6)

### GROUP 4: Auth Tables (Users, Sessions, Tokens) (4 → 1 migration)
**New file**: `V3__auth_tables.sql`
- V16__users.sql → ALTER TABLE template_users (add: failed_attempts, lock_until, credentials_updated_at, deleted_at + indexes)
- V40__create_user_sessions.sql → CREATE TABLE template_user_sessions + indexes
- V45__create_refresh_tokens.sql → CREATE TABLE template_refresh_tokens + indexes
- V54__update_user_sessions_for_device_control.sql → ALTER TABLE template_user_sessions (add: tenant_id, active + indexes)

### GROUP 5: Auth Credentials & Recovery (3 → 1 migration)
**New file**: `V4__auth_credentials.sql`
- V42__password_reset_token.sql → UNCLEAR - need to check
- V43__add_credentials_updated_at_to_users.sql → ALTER template_users (already in V16, may be duplicate)
- V44__update_password_reset_token.sql → UNCLEAR - need to check
- V23__add_device_fingerprint_to_refresh_tokens.sql → ALTER TABLE template_refresh_tokens (add device_fingerprint + index)

### GROUP 6: Vendors Core Tables (5 → 1 migration)
**New file**: `V5__vendors_core.sql`
- V3__create_vendors.sql → CREATE TABLE public.vendors + indexes
- V4__add_vendor_subscription_tracking.sql → ALTER vendors (add: subscription_expires_at, external_subscription_id + indexes)
- V7__create_subscription_history.sql → CREATE TABLE subscription_history
- V8__create_vendor_usage.sql → CREATE TABLE public.vendor_usage (quota tracking) + indexes
- V63__refactor_subscriptions_table.sql → ALTER subscriptions table

### GROUP 7: Vendor Fields & Attributes (12 → 1 migration)
**New file**: `V6__vendor_attributes.sql`
- V41__add_logo_url_to_vendors.sql → ALTER vendors ADD logo_url
- V46__add_cor_destaque_to_vendors.sql → ALTER vendors ADD cor_destaque
- V47__add_mensagem_boas_vindas.sql → ALTER vendors ADD mensagem_boas_vindas
- V48__add_vendor_fields.sql → ALTER vendors (add multiple fields)
- V49__add_schema_name_to_vendors.sql → ALTER vendors ADD schema_name
- V50__add_slug_to_vendors.sql → ALTER vendors ADD slug
- V55__add_subscription_fields_to_vendors.sql → ALTER vendors (subscription fields)
- V56__add_missing_vendor_billing_columns.sql → ALTER vendors (billing columns)
- V57__ensure_vendor_subscription_core_columns.sql → ALTER vendors (ensure columns)
- V58__ensure_vendor_entity_core_columns.sql → ALTER vendors (ensure core columns)
- (Plus corresponding indexes in consolidated migration)

### GROUP 8: Vendor Alerts & Risks (2 → 1 migration)
**New file**: `V7__vendor_alerts.sql`
- V26__create_vendor_risk_alerts.sql → CREATE TABLE vendor_risk_alerts
- V24__add_alert_flags_to_vendor_usage.sql → ALTER vendor_usage (add: alert80_sent, alert100_sent)
- V25__add_email_events_and_vendor_email_invalid.sql → ALTER vendors (add: email_invalid) + CREATE email_events?
- V27__add_alert_flags_to_vendor_usage.sql → ALTER vendor_usage (likely duplicate of V24)

### GROUP 9: Leads Core Tables (4 → 1 migration)
**New file**: `V8__leads_core.sql`
- V17__leads.sql → ALTER template_leads (add: status, deleted_at, constraint, indexes)
- V18__lead_status_history.sql → CREATE TABLE lead_status_history
- V28__create_vendor_leads.sql → CREATE TABLE vendor_leads
- V32__create_vendor_lead_stage_history.sql → CREATE TABLE vendor_lead_stage_history

### GROUP 10: Leads Attributes (5 → 1 migration)
**New file**: `V9__leads_attributes.sql`
- V29__create_vendor_lead_alerts.sql → CREATE TABLE vendor_lead_alerts
- V30__create_vendor_lead_conversations.sql → CREATE TABLE vendor_lead_conversations
- V33__add_score_and_resumo_to_vendor_leads.sql → ALTER vendor_leads (add: score, resumo_estrategico)
- V34__add_owner_to_vendor_leads.sql → ALTER vendor_leads (add: owner_email)
- V59__ensure_vendor_leads_entity_columns.sql → ALTER vendor_leads (ensure columns)
- V60__align_vendor_leads_valor_credito_type.sql → ALTER vendor_leads (align valor_credito type)
- V61__align_vendor_leads_stage_constraint.sql → ALTER vendor_leads stage constraint

### GROUP 11: Audit & Logging (5 → 1 migration)
**New file**: `V10__audit_and_logging.sql`
- V13__create_security_audit_logs.sql → CREATE TABLE security_audit_logs
- V14__Create_system_audit_logs.sql → CREATE TABLE system_audit_logs
- V15__create_audit_logs.sql → CREATE TABLE audit_logs
- V20__logs.sql → CREATE TABLE logs
- V31__create_vendor_audit_logs.sql → CREATE TABLE vendor_audit_logs
- V38__create_login_audit_table.sql → CREATE TABLE login_audit
- V52__fix_login_audit_schema.sql → ALTER login_audit schema

### GROUP 12: Billing & Payments (3 → 1 migration)
**New file**: `V11__billing_and_payments.sql`
- V10__create_payment_events.sql → CREATE TABLE payment_events
- V11__create_payment_checkout_requests.sql → CREATE TABLE checkout_requests
- V12__webhook_events_and_last_payment.sql → CREATE TABLE + ALTER vendors

### GROUP 13: Vendor Features (1 → 1 migration)
**Keep as**: `V12__vendor_features.sql`
- V36__create_vendor_features.sql → CREATE TABLE vendor_features

### GROUP 14: Plans & Usage Limits (3 → 1 migration)
**New file**: `V13__plans_and_quotas.sql`
- V62__create_plans_table.sql → CREATE TABLE plans + seed "Leadflow Standard"
- V64__create_usage_limits_table.sql → CREATE TABLE usage_limits
- V72__ensure_plans_seeded.sql → Safety check INSERT INTO plans

### GROUP 15: Auth Seeds - Admin Users (2 → 1 migration)
**New file**: `V14__seed_admin_users.sql`
- V21__seed_default_admin_user.sql → INSERT INTO users (default admin) with schema/table existence check
- V70__seed_admin_test_user.sql → INSERT INTO users (test admin) with schema/table existence check
- V71__assign_admin_role_to_test_user.sql → UPDATE user role assignment (or combine into admin seed)

### GROUP 16: Performance Indexes (3 → 1 migration)
**New file**: `V15__performance_indexes.sql`
- V9__add_production_performance_indexes.sql → CREATE INDEXES (conditional)
- V22__add_production_performance_indexes.sql → CREATE INDEXES (likely duplicate, consolidate)
- V35__email_retry_and_composite_indexes.sql → CREATE INDEXES for email retry

### GROUP 17: Data Migrations / Normalization (2 → 1 migration)
**New file**: `V16__data_normalization.sql`
- V51__update_stage_to_uppercase.sql → UPDATE vendor_leads stage to UPPERCASE
- V53__update_stage_to_uppercase.sql → UPDATE (likely duplicate of V51)

---

## CONSOLIDATION SUMMARY

**Before**: 68 migrations
**After**: 17 migrations (grouped by domain)

| After Consolidation | Count | Files to Consolidate |
|---|---|---|
| V0__core_infrastructure.sql | 1 | V0, V1, V2 |
| V1__global_and_template_tables.sql | 1 | V5, V19 |
| V2__seed_core_roles.sql | 1 | V6, V37 |
| V3__auth_tables.sql | 1 | V16, V40, V45, V54 |
| V4__auth_credentials.sql | 1 | V23, V42, V43, V44 |
| V5__vendors_core.sql | 1 | V3, V4, V7, V8, V63 |
| V6__vendor_attributes.sql | 1 | V41, V46, V47, V48, V49, V50, V55, V56, V57, V58 |
| V7__vendor_alerts.sql | 1 | V24, V25, V26, V27 |
| V8__leads_core.sql | 1 | V17, V18, V28, V32 |
| V9__leads_attributes.sql | 1 | V29, V30, V33, V34, V59, V60, V61 |
| V10__audit_and_logging.sql | 1 | V13, V14, V15, V20, V31, V38, V52 |
| V11__billing_and_payments.sql | 1 | V10, V11, V12 |
| V12__vendor_features.sql | 1 | V36 |
| V13__plans_and_quotas.sql | 1 | V62, V64, V72 |
| V14__seed_admin_users.sql | 1 | V21, V70, V71 |
| V15__performance_indexes.sql | 1 | V9, V22, V35 |
| V16__data_normalization.sql | 1 | V51, V53 |

---

## QUESTIONS & UNKNOWNS

1. **V5 vs V1**: Both seem to create tenants/roles. Need verification that V5 isn't redundant.
2. **V6 vs V37**: Both seed roles. Need to verify this isn't duplicate work.
3. **V9 vs V22**: Both add performance indexes. Are they identical? Can they be merged?
4. **V24 vs V27**: Both alter vendor_usage with alerts. Are they doing the same thing?
5. **V42, V43, V44**: Password reset token and credentials. Need to read full context.
6. **V51 vs V53**: Both update stage to uppercase. Likely duplicate.
7. **V71**: Assign admin role - can this be part of V21/V70 seed?

---

## NEXT STEPS

1. ✅ **Map complete** - All 68 migrations categorized
2. ⏳ **Verify unknowns** - Read the unclear migrations (V42, V43, V44, etc.)
3. ⏳ **Create new consolidated migrations** - Following the map above
4. ⏳ **Test migration run** - Run all 17 new migrations
5. ⏳ **Delete old 68 migrations** - Clean up migration folder
6. ⏳ **Mark old migrations in comments** - Document what was consolidated

---

## RISKS & MITIGATION

| Risk | Mitigation |
|---|---|
| Missing dependencies between migrations | Read all migrations before consolidating |
| Breaking existing Flyway history | Test on separate DB copy first |
| Conflicting timestamps | Consolidations use same logic as originals |
| Unknown side effects | Keep original files as backup until validated |

