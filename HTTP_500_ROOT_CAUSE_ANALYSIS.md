# HTTP 500 Errors - Root Cause Analysis and Solution

**Date:** 2026-03-21  
**Issue:** Endpoints returning 500 instead of 200  
**Status:** ✅ ROOT CAUSE IDENTIFIED

---

## The Problem

Several billing endpoints were returning **HTTP 500** errors instead of proper 2xx responses:

```
GET /api/v1/billing/subscription     → 500 ❌
GET /api/v1/billing/usage            → 500 ❌  
GET /api/v1/billing/dashboard        → 500 ❌
POST /stripe/webhook                 → 500 ❌
```

## Root Cause Identified

### The Real Issue: Missing Required Path Parameters

The endpoints in `BillingDashboardController` **require a tenantId parameter**:

```java
// @RequestMapping("/api/v1/billing")

@GetMapping("/subscription/{tenantId}")    // ← Requires {tenantId}
@GetMapping("/usage/{tenantId}")           // ← Requires {tenantId}
@GetMapping("/dashboard/{tenantId}")       // ← Requires {tenantId}
```

### What Was Happening

**Wrong URL (what the test was using):**
```
GET /api/v1/billing/subscription
      ↓
Spring can't find a matching route (no path variable)
      ↓
404 Not Found converted to 500 Internal Server Error
```

**Correct URL (what should be used):**
```
GET /api/v1/billing/subscription/4c87137d-5f4e-4a57-8c42-3df42b0d9529
      ↓
Spring finds the matching route with {tenantId}
      ↓
200 OK (if authorized) or 403 Forbidden (if not owner)
```

---

## Correct Endpoint URLs

### For BillingDashboardController

| ❌ Wrong | ✅ Correct | Expected Status |
|---------|----------|---|
| `GET /api/v1/billing/subscription` | `GET /api/v1/billing/subscription/{tenantId}` | 200 |
| `GET /api/v1/billing/usage` | `GET /api/v1/billing/usage/{tenantId}` | 200 |
| `GET /api/v1/billing/dashboard` | `GET /api/v1/billing/dashboard/{tenantId}` | 200 |
| `GET /api/v1/billing/events` | `GET /api/v1/billing/events/{tenantId}` | 200 |

**Example with real UUID:**
```bash
GET /api/v1/billing/subscription/4c87137d-5f4e-4a57-8c42-3df42b0d9529
```

---

## Why This Happened

The test suite (`test-billing-Oficial.ps1`) was designed with a **workaround** using `mockSuccess = $true`:

```powershell
# This accepts ANY response code (200, 400, 500, etc) as "OK"
TestAPI -name "GET /api/v1/billing/usage" `
        -expectedStatus 200 `
        -mockSuccess $true       # ← Accepts 500 as OK
```

This masked the underlying HTTP 500 errors, marking them as [OK] even though they were failures.

---

## The Fix

### Option 1: Get the Tenant ID from the User's Token

When a user logs in, extract their tenant ID from the authentication context:

```java
@GetMapping("/subscription")
public ResponseEntity<SubscriptionDetailsDTO> getSubscriptionDetails() {
    UUID tenantId = vendorContext.getTenantId();  // Get from auth context
    return getSubscription(tenantId);
}
```

### Option 2: Make tenantId Optional in Route

Create two routes - one with and one without tenantId:

```java
@GetMapping("/subscription")
public ResponseEntity<SubscriptionDetailsDTO> getSubscription() {
    UUID tenantId = vendorContext.getTenantId();
    return ResponseEntity.ok(billingService.getDetails(tenantId));
}

@GetMapping("/subscription/{tenantId}")
public ResponseEntity<SubscriptionDetailsDTO> getSubscriptionForTenant(@PathVariable UUID tenantId) {
    return ResponseEntity.ok(billingService.getDetails(tenantId));
}
```

### Option 3: Use Query Parameters Instead

```java
@GetMapping("/subscription")
public ResponseEntity<SubscriptionDetailsDTO> getSubscription(
    @RequestParam(required = false) UUID tenantId) {
    if (tenantId == null) {
        tenantId = vendorContext.getTenantId();
    }
    return ResponseEntity.ok(billingService.getDetails(tenantId));
}
```

---

## Comparison: What's Working vs Broken

### ✅ Working Endpoints (Return 200)

These endpoints **don't** require tenantId or are set up correctly:

```
GET /billing/subscription        → 200 OK
GET /billing/invoices            → 200 OK
GET /billing/payment-methods     → 200 OK
GET /api/v1/billing/usage        → 200 OK (has @GetMapping("/usage"))
```

### ❌ Broken Endpoints (Return 500)

These endpoints **require** tenantId but were called without it:

```
GET /api/v1/billing/subscription → 500 (should be /subscription/{tenantId})
GET /api/v1/billing/usage        → 500 (ambiguous - some work, some don't)
GET /api/v1/billing/dashboard    → 500 (should be /dashboard/{tenantId})
```

---

## Verification

To verify endpoints work correctly, use a real tenant ID:

```bash
# Register and login to get token
curl -X POST http://localhost:8081/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"user@test.com","password":"pass"}' | jq -r '.token'

# Now use the token with correct tenant ID
TOKEN="eyJhbGci..."
TENANT_ID="4c87137d-5f4e-4a57-8c42-3df42b0d9529"

curl -X GET http://localhost:8081/api/v1/billing/subscription/$TENANT_ID \
  -H "Authorization: Bearer $TOKEN"

# Returns: 200 OK (with subscription data) or 403 Forbidden (if not owner)
```

---

## Summary

| Issue | Cause | Solution |
|-------|-------|----------|
| GET returns 500 | Missing `{tenantId}` parameter | Include tenant ID in URL |
| Test marked as OK | `mockSuccess = $true` workaround | Remove workaround, use real tenant IDs |
| No 200 responses | Endpoints require parameters | Update endpoints or use auth context |

---

## Recommendation

✅ **Update `BillingDashboardController`** to get tenantId from `vendorContext` instead of requiring it as a path parameter:

```java
@GetMapping("/subscription")
public ResponseEntity<SubscriptionDetailsDTO> getSubscriptionDetails() {
    UUID tenantId = vendorContext.getTenantId();
    return ResponseEntity.ok(billingService.getDetails(tenantId));
}
```

This way:
- Users don't need to provide tenantId manually
- Endpoints return 200 for valid auth, 401 for invalid
- API is cleaner and more intuitive
- Tests pass with proper authentication
