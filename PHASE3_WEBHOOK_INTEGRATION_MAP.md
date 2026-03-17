# 🚀 PHASE 3: Webhook Service Integration - Complete Mapping

**Status:** Implementation Required  
**Estimated Time:** 30 minutes  
**Priority:** HIGH (Critical for billing flow)

---

## 📋 TASKS BREAKDOWN

### TASK 1: Implement SubscriptionService Methods (3 methods)

#### Method 1: `markPaymentSuccessful(String stripeSubscriptionId, String invoiceId)`
**Purpose:** Record successful payment from invoice.payment_succeeded webhook  
**Called by:** `InvoicePaymentSucceededHandler.handle(Event)`  
**Location:** `src/main/java/com/leadflow/backend/service/vendor/SubscriptionService.java`

**Requirements:**
- Find subscription by stripeSubscriptionId
- Update lastPaymentDate = now
- Update paymentStatus = "PAID"
- Save subscription record
- Record audit trail with invoiceId
- Return successfully or throw exception

**Pseudocode:**
```java
@Transactional
public void markPaymentSuccessful(String stripeSubscriptionId, String invoiceId) {
    Subscription subscription = subscriptionRepository
        .findByStripeSubscriptionId(stripeSubscriptionId)
        .orElseThrow(() -> new SubscriptionNotFoundException(stripeSubscriptionId));
    
    subscription.setLastPaymentDate(LocalDateTime.now());
    subscription.setPaymentStatus("PAID");
    subscriptionRepository.save(subscription);
    
    recordAuditTrail(subscription, "PAYMENT_SUCCESSFUL", invoiceId);
    
    log.info("Payment successful: subscriptionId={}, invoiceId={}, amount=???", 
        stripeSubscriptionId, invoiceId);
}
```

---

#### Method 2: `markAsDeletedFromStripe(String stripeSubscriptionId)`
**Purpose:** Mark subscription as cancelled when deleted from Stripe  
**Called by:** `SubscriptionDeletedHandler.handle(Event)`  
**Location:** `src/main/java/com/leadflow/backend/service/vendor/SubscriptionService.java`

**Requirements:**
- Find subscription by stripeSubscriptionId
- Set status = SubscriptionStatus.CANCELADA
- Set cancelledAt = now
- Save subscription record
- Record audit trail
- Log successful cancellation

**Pseudocode:**
```java
@Transactional
public void markAsDeletedFromStripe(String stripeSubscriptionId) {
    Subscription subscription = subscriptionRepository
        .findByStripeSubscriptionId(stripeSubscriptionId)
        .orElseThrow(() -> new SubscriptionNotFoundException(stripeSubscriptionId));
    
    subscription.setStatus(Subscription.SubscriptionStatus.CANCELLED);
    subscription.setCancelledAt(LocalDateTime.now());
    subscriptionRepository.save(subscription);
    
    recordAuditTrail(subscription, "DELETED_FROM_STRIPE", "Stripe webhook notification");
    
    log.info("Subscription cancelled: subscriptionId={}, tenantId={}", 
        stripeSubscriptionId, subscription.getTenantId());
}
```

---

#### Method 3: `syncWithStripe(Subscription subscription)`
**Purpose:** Synchronize subscription status with Stripe when updated  
**Called by:** `SubscriptionUpdatedHandler.handle(Event)`  
**Location:** `src/main/java/com/leadflow/backend/service/vendor/SubscriptionService.java`

**Requirements:**
- Update last_synced_at = now
- Update currentPeriodEnd from subscription object
- Update status based on Stripe status mapping
- Save subscription record
- Record audit trail
- Log sync details

**Pseudocode:**
```java
@Transactional
public void syncWithStripe(Subscription subscription) {
    // Subscription is already Stripe data, just save to DB
    subscription.setLastSyncedAt(LocalDateTime.now());
    subscriptionRepository.save(subscription);
    
    recordAuditTrail(subscription, "SYNCED_FROM_STRIPE", 
        "Status: " + subscription.getStatus());
    
    log.info("Subscription synced: subscriptionId={}, status={}, periodEnd={}", 
        subscription.getId(), subscription.getStatus(), 
        subscription.getCurrentPeriodEnd());
}
```

---

### TASK 2: Implement Subscription Event Handlers (Already exist but need testing)

#### Handler 1: InvoicePaymentSucceededHandler
**File:** `src/main/java/com/leadflow/backend/service/billing/InvoicePaymentSucceededHandler.java`  
**Event Type:** `invoice.payment_succeeded`  
**Status:** ✅ Already calls markPaymentSuccessful()  
**Action Required:** Test with webhook

**Webhook Flow:**
```
Stripe → POST /webhook → StripeWebhookController
  ↓
Parse Event (invoice.payment_succeeded)
  ↓
Find Handler (InvoicePaymentSucceededHandler)
  ↓
handler.handle(event)
  ↓
subscriptionService.markPaymentSuccessful(invoice.getSubscription(), invoice.getId())
  ↓
Update DB + Audit Log
```

---

#### Handler 2: SubscriptionDeletedHandler
**File:** `src/main/java/com/leadflow/backend/service/billing/SubscriptionDeletedHandler.java`  
**Event Type:** `customer.subscription.deleted`  
**Status:** ✅ Already calls markAsDeletedFromStripe()  
**Action Required:** Test with webhook

**Webhook Flow:**
```
Stripe → POST /webhook → StripeWebhookController
  ↓
Parse Event (customer.subscription.deleted)
  ↓
Find Handler (SubscriptionDeletedHandler)
  ↓
handler.handle(event)
  ↓
subscriptionService.markAsDeletedFromStripe(subscription.getId())
  ↓
Update DB + Audit Log
```

---

#### Handler 3: SubscriptionUpdatedHandler
**File:** `src/main/java/com/leadflow/backend/service/billing/SubscriptionUpdatedHandler.java`  
**Event Type:** `customer.subscription.updated`  
**Status:** ✅ Already calls syncWithStripe()  
**Action Required:** Test with webhook

**Webhook Flow:**
```
Stripe → POST /webhook → StripeWebhookController
  ↓
Parse Event (customer.subscription.updated)
  ↓
Find Handler (SubscriptionUpdatedHandler)
  ↓
handler.handle(event)
  ↓
subscriptionService.syncWithStripe(subscription)
  ↓
Update DB + Audit Log
```

---

## 🔌 WEBHOOK ENDPOINTS

All webhooks use the same receiver:

| Endpoint | Method | Purpose | Handler |
|----------|--------|---------|---------|
| `/webhook` | POST | Receive Stripe events | StripeWebhookController |
| `/billing/webhook` | POST | Billing webhook receiver | BillingWebhookController (alias) |

**Both endpoints** accept and validate Stripe webhooks with:
- ✅ HMAC-SHA256 signature verification
- ✅ Timestamp validation (5 min tolerance)
- ✅ Idempotency checking
- ✅ Event persistence

---

## 📊 SUBSCRIPTION FIELDS

**Subscription Entity Properties:**
```
- id (Long)
- tenantId (UUID)
- stripeCustomerId (String)
- stripeSubscriptionId (String) ← KEY for lookups
- status (Subscription.SubscriptionStatus)
- plan (Plan)
- startedAt (LocalDateTime)
- expiresAt (LocalDateTime)
- lastPaymentDate (LocalDateTime) ← Updated by markPaymentSuccessful()
- paymentStatus (String) ← Updated by markPaymentSuccessful()
- cancelledAt (LocalDateTime) ← Updated by markAsDeletedFromStripe()
- lastSyncedAt (LocalDateTime) ← Updated by syncWithStripe()
- currentPeriodEnd (LocalDateTime)
```

---

## 🧪 TESTING APPROACH

### Phase 3a: Unit Tests (15 min)
1. Test `markPaymentSuccessful()` - verify DB updates
2. Test `markAsDeletedFromStripe()` - verify status change
3. Test `syncWithStripe()` - verify sync timestamp

### Phase 3b: Integration Tests (15 min)
1. Mock Stripe webhook with `invoice.payment_succeeded`
2. Mock Stripe webhook with `customer.subscription.deleted`
3. Mock Stripe webhook with `customer.subscription.updated`

---

## 📝 AUDIT TRAIL

Each operation should record:
- Subscription ID
- Previous state
- New state
- Change reason (PAYMENT_SUCCESSFUL, DELETED_FROM_STRIPE, SYNCED_FROM_STRIPE)
- External event ID (Stripe event ID)
- Timestamp

---

## ✅ COMPLETION CHECKLIST

- [ ] Implement `markPaymentSuccessful()` method
- [ ] Implement `markAsDeletedFromStripe()` method
- [ ] Implement `syncWithStripe()` method
- [ ] Verify handlers compile
- [ ] Create test script for webhook simulation
- [ ] Test all 3 webhook event types
- [ ] Verify audit trails recorded
- [ ] All 3 methods tested and working
- [ ] Update FINAL_ENDPOINTS_STATUS.md

---

## 🎯 SUCCESS CRITERIA

**Phase 3 Complete when:**
- ✅ All 3 SubscriptionService methods implemented
- ✅ All 3 webhook handlers tested (receive event → update DB)
- ✅ 100% of webhook events processed correctly
- ✅ Audit trails recorded for all changes
- ✅ No errors in logs

**Metrics:**
- Payment success rate: 100%
- Subscription cancellation handling: 100%
- Sync accuracy: 100%

---

*Phase 3 is the critical link between Stripe webhook delivery and database state synchronization*
