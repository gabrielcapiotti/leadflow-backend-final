# Lead Endpoints HTTP 500 - Root Cause Analysis

## Problem Summary
- **Endpoint**: GET/POST `/api/leads`
- **Status**: HTTP 500 Internal Server Error
- **Affected Tests**: 2/3 endpoints failing
- **Working Endpoint**: GET `/api/vendor-leads/metrics` (HTTP 200)

## Root Cause Identified

### Issue Chain:
```
User Registration (AuthController)
  ↓
User Created ✅
  ↓
Vendor NOT Created ❌
  ↓
Lead Endpoint Request
  ↓
enforceWriteAccess() calls subscriptionGuard.resolveAccess()
  ↓
resolveVendor() calls vendorRepository.findByUserEmail(email)
  ↓
No vendor found in database ❌
  ↓
Throws AccessDeniedException("Vendor not found for user")
  ↓
No specific exception handler in LeadController
  ↓
Falls through to generic Exception handler
  ↓
Returns HTTP 500 ❌
```

### Code References:

**1. AuthService.registerUser() - Lines 65-115**
```java
// Only creates User, NOT Vendor
User user = new User(name.trim(), normalizedEmail, passwordEncoder.encode(password), userRole);
userRepository.save(user);
// Missing: vendorService.createVendor(user)
```

**2. SubscriptionGuard.resolveVendor() - Lines 85-107**
```java
return vendorRepository
    .findByUserEmail(email)
    .stream()
    .findFirst()
    .orElseThrow(() -> 
        new AccessDeniedException("Vendor not found for user")  // ← Exception thrown here
    );
```

**3. LeadController.createLead() - Lines 53-78**
```java
try {
    enforceWriteAccess();  // ← Calls subscriptionGuard, exception NOT caught here
    User user = resolveAuthenticatedUser(principal);
    Lead lead = leadService.createLead(...);
    return ResponseEntity.status(HttpStatus.CREATED).body(new LeadResponse(lead));
} catch (IllegalArgumentException e) {
    return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(null);
} catch (Exception e) {
    log.error("Unexpected error during lead creation", e);  // ← Generic handler
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(null);  // ← Returns 500
}
```

## Why `/api/vendor-leads/metrics` Works

**VendorLeadService.getMetricsForCurrentVendor() - Line 350**
```java
public VendorLeadMetricsResponse getMetricsForCurrentVendor() {
    try {
        UUID vendorId = vendorContext.getCurrentVendor().getId();
        // ... get metrics ...
        return new VendorLeadMetricsResponse(map);
    } catch (Exception e) {
        log.error("Error getting metrics for current vendor", e);
        return new VendorLeadMetricsResponse(new HashMap<>());  // ← Returns empty metrics
    }
}
```

**Result**: HTTP 200 with empty response, NOT error

## Test Results:

```
ENDPOINT TEST SUMMARY:
═══════════════════════════════════════════════════════

✅ GET /api/vendor-leads/metrics  (HTTP 200) - Returns empty HashMap
❌ GET /api/leads (HTTP 500)       - Vendor not found
❌ POST /api/leads (HTTP 500)      - Vendor not found

Pass Rate: 33% (1/3)
```

## Workarounds Attempted:

1. ✅ **Create vendor after user registration**
   - Command: `POST /api/vendors` with `userEmail`
   - Result: HTTP 400 (invalid request body format)
   - Issue: Endpoint expects different field format

2. ❌ **Pass vendor in test creation**
   - Issue: No vendor creation endpoint accessible to regular users

## Recommended Solutions:

### Option 1: Auto-Create Vendor on Registration (BEST)
Modify `AuthService.registerUser()`:
```java
User user = userRepository.save(new User(...));
vendorService.createVendor(user.getEmail());  // Auto-create
return user;
```

### Option 2: Auto-Create Vendor on First Lead Access
Modify `LeadController.createLead()`:
```java
if (subscriptionGuard.resolveAccess() == SubscriptionAccessLevel.VENDOR_NOT_FOUND) {
    vendorService.createVendor(user.getEmail());
}
```

### Option 3: Create Vendor via Onboarding Endpoint
Create new endpoint: `POST /auth/onboard` that:
1. Creates vendor for authenticated user
2. Sets up trial subscription
3. Initializes usage limits

## Impact:
- **Current**: Users cannot use leads endpoints
- **After Fix**: All endpoints functional  
- **Test Pass Rate**: Will increase from 33% to 100%

---
**Date**: 2026-03-19
**Status**: Root cause identified, solution pending implementation
