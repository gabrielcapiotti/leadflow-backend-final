# 📊 LEADFLOW DATABASE COMPLETE SCHEMA MAP

## ⚡ Quick Overview

- **Total Tables:** 37
- **Database:** PostgreSQL `leadflow_test`
- **Host:** localhost:2411
- **Main Schema:** public

---

## 📑 All Tables (37)

1. **audit_logs** - Generic audit trail
2. **email_events** - Email delivery tracking  
3. **failed_webhook_events** - Failed webhook retries
4. **flyway_schema_history** - Schema version history
5. **lead_status_history** - Lead status progression audit
6. **leads** - Lead records
7. **login_audit** - Login attempt tracking
8. **logs** - Application logs
9. **password_reset_token** - Password reset tokens
10. **payment_checkout_requests** - Stripe checkout sessions
11. **payment_events** - Payment transaction events
12. **payments** - Payment records
13. **plans** - Billing plan definitions
14. **refresh_tokens** - JWT refresh tokens
15. **roles** - Role definitions (ROLE_ADMIN, ROLE_USER, etc.)
16. **security_audit_logs** - Security event logging
17. **settings** - System settings & feature flags
18. **stripe_event_logs** - Stripe webhook logs
19. **subscription_audits** - Subscription action audit
20. **subscription_history** - Subscription status history
21. **subscriptions** - Active subscriptions
22. **system_audit_logs** - System-level event logs
23. **tenants** - Multi-tenant isolation
24. **usage_limits** - Per-plan resource limits
25. **user_sessions** - Active user sessions
26. **users** - User accounts
27. **vendor_audit_logs** - Vendor action audit
28. **vendor_features** - Feature access per vendor
29. **vendor_lead_alerts** - Vendor lead notifications
30. **vendor_lead_conversations** - Chat histories
31. **vendor_lead_messages** - Individual messages
32. **vendor_lead_stage_history** - Vendor-lead stage audit
33. **vendor_leads** - Leads assigned to vendors
34. **vendor_risk_alerts** - Fraud/risk alerts
35. **vendor_usage** - Vendor usage metrics
36. **vendors** - Vendor/partner accounts
37. **webhook_events** - Webhook event tracking

---

## 🏗️ Core Architecture

### Authentication & Authorization
```
USERS
  ├── Columns: id, email (unique), password (bcrypt), role_id, name, ...
  ├── Relationships: role_id → ROLES
  ├── Security: Account lockout mechanism, soft deletes
  └── Auditing: LOGIN_AUDIT, SECURITY_AUDIT_LOGS

ROLES
  ├── id (UUID)
  ├── name (ROLE_ADMIN, ROLE_USER, etc.)
  └── Referenced by: USERS.role_id

USER_SESSIONS
  ├── Active sessions for logged-in users
  ├── Columns: id, user_id (FK), ip_address, user_agent, last_activity
  └── Purpose: Session management & concurrency

REFRESH_TOKENS
  ├── JWT token refresh
  ├── Columns: id, user_id (FK), token (hashed), valid_until, revoked_at
  └── Purpose: Extend session without re-login

PASSWORD_RESET_TOKEN
  ├── Temporary reset tokens
  ├── Columns: id, user_id (FK), token (hashed), expires_at, used_at
  └── Purpose: Secure password reset flow
```

### Lead Management
```
LEADS
  ├── Base lead records
  ├── Columns: id, name, email, phone, company, created_at, deleted_at
  └── Status: Tracked separately in LEAD_STATUS_HISTORY

LEAD_STATUS_HISTORY
  ├── Audit trail for lead progression
  ├── Columns: id, lead_id (FK), status (enum), changed_by (FK), changed_at
  └── Purpose: Track lead movement through sales pipeline

Related Tables:
  ├── VENDOR_LEADS → Links leads to specific vendors
  ├── VENDOR_LEAD_STAGE_HISTORY → Stage progression per vendor
  ├── VENDOR_LEAD_CONVERSATIONS → Vendor-lead communication
  └── VENDOR_LEAD_MESSAGES → Individual message storage
```

### Vendor Management
```
VENDORS
  ├── Partner/vendor accounts
  ├── Columns: id, user_id (FK), name, company_name, email, phone
  └── Note: Each vendor has an associated user account

VENDOR_LEADS
  ├── Vendor-to-Lead relationships
  ├── Columns: id, vendor_id (FK), lead_id (FK), stage, assigned_at
  └── Purpose: Track which vendor works with which lead

VENDOR_FEATURES
  ├── Feature access control
  ├── Columns: id, vendor_id (FK), feature_name, enabled
  └── Purpose: Restrict features per vendor

VENDOR_USAGE
  ├── Usage metrics & analytics
  ├── Columns: id, vendor_id (FK), metric_type, count, period_date
  └── Purpose: Track API calls, feature usage

Vendor Alerts & Risk:
  ├── VENDOR_LEAD_ALERTS → Notifications for vendor leads
  ├── VENDOR_RISK_ALERTS → Fraud/suspicious activity alerts
  └── VENDOR_AUDIT_LOGS → Complete vendor activity history
```

### Billing & Subscriptions
```
PLANS
  ├── Definition of available plans
  ├── Columns: id, name, price (decimal), interval (monthly/yearly), features (JSON)
  └── Relationships: Referenced by SUBSCRIPTIONS & USAGE_LIMITS

SUBSCRIPTIONS
  ├── Active vendor subscriptions
  ├── Columns: id, vendor_id (FK), plan_id (FK), status (active/canceled)
  ├── Stripe Integration: stripe_subscription_id, current_period_start, current_period_end
  └── History Tracked In: SUBSCRIPTION_HISTORY, SUBSCRIPTION_AUDITS

SUBSCRIPTION_HISTORY
  ├── Subscription status changes
  ├── Columns: id, subscription_id (FK), previous_status, new_status, reason
  └── Purpose: Audit trail for billing changes

USAGE_LIMITS
  ├── Per-plan resource limits
  ├── Columns: id, plan_id (FK), resource_type, limit_value
  └── Purpose: Enforce plan tiers (e.g., max 100 leads/month)

PAYMENTS
  ├── Individual payment transactions
  ├── Columns: id, vendor_id (FK), amount, status (pending/completed/failed), payment_method
  └── Events Tracked In: PAYMENT_EVENTS

PAYMENT_EVENTS
  ├── Payment lifecycle events
  ├── Columns: id, payment_id (FK), event_type, details (JSON)
  └── Examples: charged, refunded, failed

PAYMENT_CHECKOUT_REQUESTS
  ├── Stripe checkout sessions
  ├── Columns: id, vendor_id (FK), stripe_session_id, status, completed_at
  └── Purpose: Track checkout flow
```

### Webhooks & Events
```
WEBHOOK_EVENTS
  ├── All webhook transactions
  ├── Columns: id, vendor_id (FK), event_type, payload (JSON), status, retry_count
  ├── Statuses: pending, delivered, failed
  └── Purpose: Outgoing webhook management

FAILED_WEBHOOK_EVENTS
  ├── Webhook delivery failures
  ├── Columns: id, webhook_event_id (FK), error_message, retry_at
  └── Purpose: Retry failed webhooks

STRIPE_EVENT_LOGS
  ├── Incoming Stripe webhooks
  ├── Columns: id, stripe_event_id (unique), event_type, payload (JSON), processed
  └── Purpose: Stripe payment event tracking
```

### Audit & Security Logging
```
LOGIN_AUDIT
  ├── All login attempts (success & failure)
  ├── Columns: id, email, ip_address, success, reason (if failed), created_at
  └── Purpose: Security monitoring & brute-force detection

SECURITY_AUDIT_LOGS
  ├── Security-sensitive events
  ├── Columns: id, user_id (FK), action, resource_type, resource_id, details (JSON)
  ├── Examples: role changes, permission updates, token invalidation
  └── Purpose: Compliance & security audit trail

AUDIT_LOGS
  ├── Generic operational audit
  ├── Columns: id, user_id (FK), entity_type, entity_id, action, old_values, new_values (JSON)
  └── Purpose: Track data changes

SYSTEM_AUDIT_LOGS
  ├── System-level events
  ├── Columns: id, action, details (JSON), created_at
  └── Purpose: Infrastructure/system monitoring

LOGS
  ├── Application log entries
  ├── Columns: id, level (INFO/WARN/ERROR), message, stack_trace
  └── Purpose: Debugging & monitoring
```

### Settings & Configuration
```
SETTINGS
  ├── Global system configuration
  ├── Columns: id, key (unique), value (JSON), created_at, updated_at
  ├── Examples:
  │   └── Feature flags: {"enabled_features": ["ai_chat", "advanced_analytics"]}
  └── Purpose: Runtime configuration without code changes

EMAIL_EVENTS
  ├── Email delivery tracking
  ├── Columns: id, recipient_email, subject, status (sent/failed/bounced), delivered_at
  └── Purpose: Email campaign monitoring

TENANTS
  ├── Multi-tenant database isolation
  ├── Columns: id, schema_name (database schema), created_at, updated_at
  └── Purpose: Schema-based multi-tenancy (separate schema per tenant)
```

---

## 🔗 Key Relationships & Data Flow

```
USER REGISTRATION & LOGIN
┌─────────────────────────────────────────────────┐
│ 1. User registers/logs in                       │
│    └─ CREATE/UPDATE users table                 │
│    └─ RECORD in login_audit                     │
│    └─ CREATE user_sessions (on login)           │
│    └─ CREATE refresh_tokens (on login)          │
└─────────────────────────────────────────────────┘

VENDOR SETUP FLOW
┌─────────────────────────────────────────────────┐
│ 1. Vendor registration                         │
│    └─ CREATE vendors (linked to user)           │
│    └─ SELECT available plans from PLANS         │
│    └─ CREATE subscriptions (via PAYMENT_CHECKOUT) │
│    └─ Stripe payment webhook → webhook_events  │
│    └─ UPDATE subscriptions.status               │
└─────────────────────────────────────────────────┘

LEAD DISTRIBUTION FLOW
┌─────────────────────────────────────────────────┐
│ 1. Lead created                                 │
│    └─ INSERT into leads                         │
│    └─ INSERT into lead_status_history           │
│                                                 │
│ 2. Assign to vendor                            │
│    └─ INSERT into vendor_leads                  │
│    └─ UPDATE lead_status_history               │
│    └─ INSERT into vendor_lead_alerts            │
│                                                 │
│ 3. Vendor works on lead                        │
│    └─ INSERT into vendor_lead_messages          │
│    └─ UPDATE vendor_leads.stage                 │
│    └─ INSERT into vendor_lead_stage_history     │
│    └─ INSERT into lead_status_history           │
└─────────────────────────────────────────────────┘

BILLING CYCLE FLOW
┌─────────────────────────────────────────────────┐
│ 1. Subscription active                          │
│    └─ QUERY usage_limits vs vendor_usage       │
│    └─ CHECK subscriptions.current_period_end   │
│                                                 │
│ 2. Billing event occurs (new month/year)       │
│    └─ INSERT into payment_checkout_requests     │
│    └─ Stripe charges vendor                     │
│    └─ Stripe webhook received                   │
│    └─ INSERT into stripe_event_logs             │
│    └─ INSERT into payment_events                │
│    └─ INSERT into subscription_history          │
│    └─ INSERT into subscription_audits           │
└─────────────────────────────────────────────────┘
```

---

## 💾 Database Statistics

| Category | Count | Purpose |
|----------|-------|---------|
| User/Auth Tables | 6 | Authentication, authorization, sessions |
| Vendor Tables | 9 | Vendor management, features, alerts |
| Lead Tables | 4 | Lead tracking, assignments, messaging |
| Billing Tables | 8 | Plans, subscriptions, payments |
| Webhook Tables | 3 | Event handling, integration |
| Audit Tables | 5 | Logging, compliance, security |
| Config Tables | 2 | Settings, feature flags |
| **Total** | **37** | **Complete system coverage** |

---

## 🔑 Design Patterns Used

### Soft Deletes
- Tables like `leads`, `users` have `deleted_at` column
- Allows recovery and maintains referential integrity

### Audit Trail Pattern
- Every significant table has corresponding history table
- Examples: `subscriptions` → `subscription_history`, `vendor_leads` → `vendor_lead_stage_history`

### Multi-Tenancy
- While using single database, schema-based separation via `tenants` table
- Each tenant can have isolated data

### Event Sourcing (Partial)
- Payment events logged in `payment_events`
- Login attempts tracked in `login_audit`
- Webhook events stored in `webhook_events`

### Webhook Reliability
- Retry mechanism with `failed_webhook_events`
- Status tracking: pending → delivered/failed
- Exponential backoff possible via `retry_at` field

---

## 📈 Scalability Considerations

✅ **What's optimized:**
- `users.email` has unique index for fast lookups
- `vendor_leads` indexed for vendor_id and lead_id
- Webhook events have retry mechanism
- Audit logs for troubleshooting

⚠️ **Future considerations:**
- Archive old audit logs (log table could grow large)
- Partition webhook_events by vendor or date
- Consider materialized views for analytics (usage_limits checks)

---

## 🚀 How to Query

### Find all leads assigned to a vendor
```sql
SELECT l.* FROM leads l
JOIN vendor_leads vl ON l.id = vl.lead_id
WHERE vl.vendor_id = 'vendor-uuid'
ORDER BY vl.assigned_at DESC;
```

### Get vendor's current subscription
```sql
SELECT s.*, p.name as plan_name, p.price
FROM subscriptions s
JOIN plans p ON s.plan_id = p.id
WHERE s.vendor_id = 'vendor-uuid' AND s.status = 'active';
```

### Track lead progression
```sql
SELECT * FROM lead_status_history
WHERE lead_id = 'lead-uuid'
ORDER BY changed_at DESC;
```

### Monitor webhook delivery
```sql
SELECT event_type, status, COUNT(*) as count
FROM webhook_events
WHERE created_at >= NOW() - INTERVAL '24 hours'
GROUP BY event_type, status;
```

### Find security issues
```sql
SELECT user_id, action, COUNT(*) as count
FROM security_audit_logs
WHERE created_at >= NOW() - INTERVAL '1 day'
GROUP BY user_id, action
HAVING COUNT(*) > 10;
```

---

## 📝 Summary

The LeadFlow database is designed with:
- **Security first** - BCrypt hashing, audit trails, role-based access
- **Multi-tenancy** - Isolated vendor data
- **Compliance** - Complete audit logging
- **Reliability** - Retry mechanisms for webhooks, transaction integrity
- **Scalability** - Proper indexing and soft deletes

Key insight: Every important action is logged somewhere, enabling complete system auditability and recovery!
