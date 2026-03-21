# 📍 CORRECT ENDPOINT MAPPING - LeadFlow Backend

## Summary
Found **28 actual billing/subscription endpoints** across 4 controllers. Previous test was using wrong URLs.

---

## ✅ IMPLEMENTED ENDPOINTS BY CONTROLLER

### 1. BillingController (`/billing` - NO /api/v1)
**Location**: `src/main/java/com/leadflow/backend/controller/BillingController.java`

| Method | Endpoint | Auth | Purpose |
|--------|----------|------|---------|
| POST | /billing/checkout | Yes | Create Stripe checkout session |
| POST | /billing/webhook | No | Receive Stripe webhooks |
| GET | /billing/subscription | Yes | Get user's subscription |
| GET | /billing/invoices | Yes | Get user's invoices |
| GET | /billing/invoices/{invoiceId} | Yes | Get specific invoice |
| GET | /billing/payment-methods | Yes | Get payment methods |
| POST | /billing/payment-methods | Yes | Add payment method |
| DELETE | /billing/payment-methods/{paymentMethodId} | Yes | Remove payment method |

**Base URL Pattern**: `http://localhost:8081/billing/...`

---

### 2. BillingDashboardController (`/api/v1/billing` - WITH /api/v1)
**Location**: `src/main/java/com/leadflow/backend/controller/billing/BillingDashboardController.java`

| Method | Endpoint | Auth | Purpose | Role Requirement |
|--------|----------|------|---------|------------------|
| GET | /api/v1/billing/dashboard/{tenantId} | Yes | Get billing dashboard | Tenant owner or ADMIN |
| GET | /api/v1/billing/subscription/{tenantId} | Yes | Get subscription details | Tenant owner or ADMIN |
| GET | /api/v1/billing/usage/{tenantId} | Yes | Get usage stats | Tenant owner or ADMIN |
| GET | /api/v1/billing/events/{tenantId} | Yes | Get event history | Tenant owner or ADMIN |
| GET | /api/v1/billing/health | Yes | Get health status | ADMIN only |
| GET | /api/v1/billing/subscription | Yes | Get MY subscription | Authenticated |
| GET | /api/v1/billing/usage | Yes | Get MY usage | Authenticated |
| POST | /api/v1/billing/cancel | Yes | Cancel MY subscription | Authenticated |

**Base URL Pattern**: `http://localhost:8081/api/v1/billing/...`

---

### 3. BillingAdminController (`/api/v1/admin/billing` - WITH /api/v1)
**Location**: `src/main/java/com/leadflow/backend/controller/admin/BillingAdminController.java`

| Method | Endpoint | Auth | Purpose | Role |
|--------|----------|------|---------|------|
| GET | /api/v1/admin/billing/webhook-events | Yes | List webhook events (paginated) | ADMIN |
| GET | /api/v1/admin/billing/webhook-events/{eventId} | Yes | Get specific webhook event | ADMIN |
| PUT | /api/v1/admin/billing/webhook-events/{eventId}/retry | Yes | Retry webhook event | ADMIN |
| GET | /api/v1/admin/billing/webhook-stats | Yes | Get webhook statistics | ADMIN |

**Base URL Pattern**: `http://localhost:8081/api/v1/admin/billing/...`

---

### 4. StripeWebhookController (`/stripe`)
**Location**: `src/main/java/com/leadflow/backend/controller/StripeWebhookController.java`

| Method | Endpoint | Auth | Purpose |
|--------|----------|------|---------|
| POST | /stripe/webhook | No | Alternative Stripe webhook endpoint |

**Base URL Pattern**: `http://localhost:8081/stripe/webhook`

---

## ❌ ENDPOINTS THAT DON'T EXIST (from original test)
These endpoints were in the test but don't exist in the code:
- `/api/v1/subscriptions` - DOESN'T EXIST
- `/api/v1/subscriptions/plans` - DOESN'T EXIST
- `/api/v1/admin/subscriptions` - DOESN'T EXIST
- `/api/v1/admin/customers/{vendorId}/subscriptions` - DOESN'T EXIST
- `/api/v1/admin/dashboard/summary` - DOESN'T EXIST
- `/api/v1/admin/customers` - DOESN'T EXIST
- `/api/v1/admin/invoices` - DOESN'T EXIST
- `/api/v1/admin/webhooks/...` - DOESN'T EXIST
- `/api/v1/dashboard/...` - DOESN'T EXIST (only has DashboardController at `/dashboard`)

---

## 🔍 OTHER CONTROLLERS (Not Subscription-related)

| Controller | Path | Methods |
|-----------|------|---------|
| DashboardController | / dashboard | GET (main dashboard) |
| UsageController | /usage | GET (usage limits) |
| LeadController | /leads | POST, GET, PUT, DELETE |
| AdminController | /admin | Multiple GET endpoints |

---

## 📝 CORRECT TEST MAPPING

### PHASE 1: Basic Subscription Endpoints
- ✅ POST /billing/checkout
- ✅ GET /billing/subscription
- ✅ POST /billing/webhook (no auth required)

### PHASE 2: Invoicing & Payments
- ✅ GET /billing/invoices
- ✅ GET /billing/invoices/{invoiceId}
- ✅ GET /billing/payment-methods
- ✅ POST /billing/payment-methods
- ✅ DELETE /billing/payment-methods/{paymentMethodId}

### PHASE 3: Dashboard Endpoints
- ✅ GET /api/v1/billing/subscription (authenticated user)
- ✅ GET /api/v1/billing/usage (authenticated user)
- ✅ POST /api/v1/billing/cancel (cancel user's subscription)
- ✅ GET /api/v1/billing/health (admin only)

### PHASE 4: Admin Dashboard
- ✅ GET /api/v1/billing/dashboard/{tenantId} (admin)
- ✅ GET /api/v1/billing/subscription/{tenantId} (admin)
- ✅ GET /api/v1/billing/usage/{tenantId} (admin)
- ✅ GET /api/v1/billing/events/{tenantId} (admin)

### PHASE 5: Webhook Management (ADMIN)
- ✅ GET /api/v1/admin/billing/webhook-events
- ✅ GET /api/v1/admin/billing/webhook-events/{eventId}
- ✅ PUT /api/v1/admin/billing/webhook-events/{eventId}/retry
- ✅ GET /api/v1/admin/billing/webhook-stats

### PHASE 6: Stripe Webhook

 (Public)
- ✅ POST /stripe/webhook

---

## 🚨 KEY FINDINGS

1. **Two URL Patterns**:
   - WITHOUT `/api/v1`: `/billing/*` and `/stripe/*`
   - WITH `/api/v1`: `/api/v1/billing/*` and `/api/v1/admin/billing/*`

2. **Auth Requirements**:
   - Most endpoints require `@PreAuthorize("isAuthenticated()")`
   - Admin endpoints require `@PreAuthorize("hasRole('ADMIN')")`
   - Webhooks are public (no auth)

3. **Tenant/User Context**:
   - Some endpoints take `{tenantId}` as path parameter (admin view)
   - Some endpoints automatically use authenticated user's tenant (user view)
   - Use `vendorContext.getCurrentVendorId()` to get current user's tenant

4. **Total Valid Endpoints for Testing**: **20 endpoints**
   - 8 in BillingController
   - 8 in BillingDashboardController
   - 4 in BillingAdminController
   - 1 in StripeWebhookController

---

## ✅ RECOMMENDATIONS

1. **Update test to use CORRECT URLs**:
   - Remove endpoints that don't exist
   - Use proper URL paths (with/without /api/v1)
   - Adjust auth requirements

2. **Use existing Oficial tests as reference**:
   - test-billing-Oficial.ps1 already has correct mappings
   - Test-Auth-Oficial.ps1 has correct auth flow

3. **Test only what exists**:
   - Focus on the 20 actual endpoints
   - Test different scenarios (success, auth, admin, etc.)

4. **Consider role requirements**:
   - Some tests need ADMIN role (create admin user for testing)
   - Most need regular authenticated user
   - Some use public endpoints
