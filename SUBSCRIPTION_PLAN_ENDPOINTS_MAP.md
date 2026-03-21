# Subscription & Plan Endpoints Complete Map

**Total Endpoints**: 28 endpoints distribuídos em 5 controllers
**Status**: 🔴 NOT TESTED (0/28)
**Last Updated**: 2026-03-21

---

## 1. Main Billing Controller (`/billing` - No /api prefix)

**Context**: Stripe integration endpoints para operações de pagamento e assinatura
**Authentication**: Mix of `@PreAuthorize("@subscriptionGuard.isActive()")` e acesso público

### 1.1 Checkout Operations
| # | Method | Endpoint | Auth | Description | Query Params |
|---|--------|----------|------|-------------|--------------|
| 1 | `POST` | `/billing/checkout` | Public | Create Stripe checkout session | `email`, `tenantId` (optional) |
| 2 | `POST` | `/billing/webhook` | Public | Handle Stripe webhook events | `Stripe-Signature` header |

### 1.2 Subscription Details
| # | Method | Endpoint | Auth | Description | Query Params |
|---|--------|----------|------|-------------|--------------|
| 3 | `GET` | `/billing/subscription` | @subscriptionGuard.isActive() | Get subscription details | - |

### 1.3 Invoices Management
| # | Method | Endpoint | Auth | Description | Query Params |
|---|--------|----------|------|-------------|--------------|
| 4 | `GET` | `/billing/invoices` | @subscriptionGuard.isActive() | List invoices (paginated) | `limit` (default 10, max 100), `startingAfter` |
| 5 | `GET` | `/billing/invoices/{invoiceId}` | @subscriptionGuard.isActive() | Get specific invoice | - |

### 1.4 Payment Methods Management
| # | Method | Endpoint | Auth | Description | Query Params |
|---|--------|----------|------|-------------|--------------|
| 6 | `GET` | `/billing/payment-methods` | @subscriptionGuard.isActive() | List payment methods | - |
| 7 | `POST` | `/billing/payment-methods` | @subscriptionGuard.isActive() | Add payment method | `paymentMethodId` (param) |
| 8 | `DELETE` | `/billing/payment-methods/{paymentMethodId}` | @subscriptionGuard.isActive() | Remove payment method | - |

---

## 2. Billing Dashboard Controller (`/api/v1/billing`)

**Context**: Dashboard completo de faturamento com acesso por tenant
**Authentication**: Mix of `@PreAuthorize("@securityService.isTenantOwner(#tenantId)")` e `isAuthenticated()`

### 2.1 Tenant-Scoped Endpoints (Require TenantId in Path)
| # | Method | Endpoint | Auth | Description | Query Params |
|---|--------|----------|------|-------------|--------------|
| 9 | `GET` | `/api/v1/billing/dashboard/{tenantId}` | @securityService.isTenantOwner | Get complete billing dashboard | - |
| 10 | `GET` | `/api/v1/billing/subscription/{tenantId}` | @securityService.isTenantOwner | Get subscription details (admin) | - |
| 11 | `GET` | `/api/v1/billing/events/{tenantId}` | @securityService.isTenantOwner | Get Stripe event history | `limit` (default 20) |
| 12 | `GET` | `/api/v1/billing/usage/{tenantId}` | @securityService.isTenantOwner | Get usage statistics | - |

### 2.2 System Admin Endpoints
| # | Method | Endpoint | Auth | Description | Query Params |
|---|--------|----------|------|-------------|--------------|
| 13 | `GET` | `/api/v1/billing/health` | hasRole('ADMIN') | Get billing system health | - |

### 2.3 Authenticated User Endpoints (No TenantId Path Param)
| # | Method | Endpoint | Auth | Description | Query Params |
|---|--------|----------|------|-------------|--------------|
| 14 | `GET` | `/api/v1/billing/subscription` | isAuthenticated() | Get my subscription | - |
| 15 | `GET` | `/api/v1/billing/usage` | isAuthenticated() | Get my usage statistics | - |
| 16 | `POST` | `/api/v1/billing/cancel` | isAuthenticated() | Cancel my subscription | - |

---

## 3. Admin Billing Controller (`/api/v1/admin/billing`)

**Context**: Admin endpoints para gerenciamento de eventos webhook
**Authentication**: `@PreAuthorize("hasRole('ADMIN')")`

### 3.1 Webhook Event Management
| # | Method | Endpoint | Auth | Description | Query Params |
|---|--------|----------|------|-------------|--------------|
| 17 | `GET` | `/api/v1/admin/billing/webhook-events` | hasRole('ADMIN') | List webhook events (paginated) | `page` (default 0), `size` (default 20), `status` (filter) |
| 18 | `GET` | `/api/v1/admin/billing/webhook-events/{eventId}` | hasRole('ADMIN') | Get specific webhook event details | - |
| 19 | `PUT` | `/api/v1/admin/billing/webhook-events/{eventId}/retry` | hasRole('ADMIN') | Retry failed webhook event | - |

### 3.2 Webhook Statistics
| # | Method | Endpoint | Auth | Description | Query Params |
|---|--------|----------|------|-------------|--------------|
| 20 | `GET` | `/api/v1/admin/billing/webhook-stats` | hasRole('ADMIN') | Get webhook aggregated stats | - |

---

## 4. Usage Controller (`/usage`)

**Context**: Quota e limite de uso endpoints
**Authentication**: `@PreAuthorize("@subscriptionGuard.isActive()")`

| # | Method | Endpoint | Auth | Description | Query Params |
|---|--------|----------|------|-------------|--------------|
| 21 | `GET` | `/usage` | @subscriptionGuard.isActive() | Get current usage | - |
| 22 | `GET` | `/usage/limits` | @subscriptionGuard.isActive() | Get usage limits | - |

---

## 5. Webhook Replay Controller (`/api/billing/webhooks`)

**Context**: Webhook retry queue management
**Authentication**: Mix of public e tenant-scoped

### 5.1 Failed Webhooks Retrieval
| # | Method | Endpoint | Auth | Description | Query Params |
|---|--------|----------|------|-------------|--------------|
| 23 | `GET` | `/api/billing/webhooks/failed` | Public | Get pending webhooks | `page` (default 0), `size` (default 20) |
| 24 | `GET` | `/api/billing/webhooks/failed/permanent` | Public | Get permanently failed webhooks | `page` (default 0), `size` (default 20) |
| 25 | `GET` | `/api/billing/webhooks/failed/recent` | Public | Get recent failures (last 24h) | `page` (default 0), `size` (default 20) |

### 5.2 Webhook Management
| # | Method | Endpoint | Auth | Description | Query Params |
|---|--------|----------|------|-------------|--------------|
| 26 | `POST` | `/api/billing/webhooks/{webhookId}/replay` | TenantScoped | Manual replay webhook | - |
| 27 | `GET` | `/api/billing/webhooks/stats` | Public | Get retry queue statistics | - |
| 28 | `DELETE` | `/api/billing/webhooks/{webhookId}` | TenantScoped | Delete webhook from queue | - |

---

## Test Data Requirements

### Pre-Requisites
- ✅ Admin token (for admin endpoints at #13, #17-20)
- ✅ Active Vendor/Tenant ID
- ✅ Vendor with active subscription (for most endpoints)
- ✅ Valid Stripe Customer ID
- ✅ Test invoice IDs
- ✅ Test payment method IDs (from Stripe)
- ✅ Test webhook event IDs
- ✅ Test webhook IDs in failed queue

### Required Test Users/Roles
1. **Vendor User** - Regular vendor with:
   - Active subscription
   - Stripe customer account
   - At least 1 invoice
   - At least 1 payment method
   - Usage within limits

2. **Admin User** - Admin role for:
   - Webhook event management
   - Billing health check
   - System statistics

3. **Test Tenant** - For tenant-scoped endpoints:
   - Valid subscription
   - Event history
   - Usage data

---

## Testing Strategy

### Phase 1: Foundation Setup (Endpoints 1-2)
- Create checkout session
- Test webhook signature validation

### Phase 2: Subscription Basic (Endpoints 3, 14)
- Get subscription details
- Verify subscription status

### Phase 3: Invoices & Payments (Endpoints 4-8, 21-22)
- List and retrieve invoices
- Manage payment methods
- Check usage limits

### Phase 4: Admin Operations (Endpoints 13, 17-20)
- Monitor webhook events
- Check system health
- Retrieve webhook statistics

### Phase 5: Dashboard & Analytics (Endpoints 9-12, 15-16)
- Access billing dashboard
- Get usage analytics
- Retrieve event history

### Phase 6: Webhook Management (Endpoints 23-28)
- List failed webhooks
- Replay failed events
- Delete webhooks

### Phase 7: Cancellation (Endpoint 16)
- Cancel subscription (end-to-end)

---

## Known Issues/Considerations

### 1. **Stripe API Integration**
- Webhook endpoints depend on valid Stripe events
- Payment method operations require Stripe API keys
- Invoice retrieval queries Stripe directly

### 2. **Multi-Tenancy**
- Some endpoints accept `tenantId` path parameter
- Tenant validation via `@securityService.isTenantOwner()`
- Subscription guard requires active subscription

### 3. **Authorization Patterns**
- Mix of role-based (`hasRole('ADMIN')`)
- Service-based (`@subscriptionGuard.isActive()`)
- Tenant-aware (`@securityService.isTenantOwner()`)

### 4. **Pagination**
- Endpoints #17, 23-25 use pagination
- Default page size: 20-30
- Max limit: varies by endpoint

---

## Success Criteria

✅ All 28 endpoints respond with appropriate HTTP status codes
✅ Authentication enforcement validated (401/403 for unauthorized)
✅ Pagination parameters work correctly
✅ Error handling returns appropriate error messages
✅ Data integrity verified (vendor/tenant isolation)
✅ Webhook processing doesn't break on malformed events

---

## Next Steps

1. [ ] Create test data setup script
2. [ ] Generate Stripe test tokens
3. [ ] Create comprehensive test suite (`test-subscription-plan-Oficial.ps1`)
4. [ ] Test endpoints in phase order
5. [ ] Update registry with results
6. [ ] Document any failures with root cause
