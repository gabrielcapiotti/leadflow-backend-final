# WEBHOOK ENDPOINTS COMPLETE CHECKLIST

## Test Execution Tracker

### Webhook Ingestion Endpoints (3/3)

**Test 1: POST /stripe/webhook**
- Endpoint: /stripe/webhook
- Method: POST
- Auth: Signature (HMAC-SHA256)
- Expected Status: 200
- Payload Type: Stripe Event JSON
- Sample Events:
  - charge.succeeded
  - charge.failed
  - payment_intent.succeeded
  - payment_intent.payment_failed
- Error Handling: 401 Unauthorized (invalid signature)
- Status: READY FOR TESTING

**Test 2: POST /webhooks/cakto**
- Endpoint: /webhooks/cakto
- Method: POST
- Auth: Custom Signature Header
- Expected Status: 200 (or 204)
- Payload Type: Cakto Lead Event JSON
- Sample Events:
  - lead.created
  - lead.updated
  - lead.qualified
  - lead.disqualified
- Error Handling: 401 Unauthorized (invalid signature)
- Status: READY FOR TESTING

**Test 3: POST /webhooks/sendgrid**
- Endpoint: /webhooks/sendgrid
- Method: POST
- Auth: Bearer Token
- Expected Status: 200 (or 204)
- Payload Type: SendGrid Event Array JSON
- Sample Events:
  - delivered
  - opened
  - clicked
  - bounced
  - complained
- Error Handling: 401 Unauthorized (invalid token)
- Status: READY FOR TESTING

---

### Monitoring & Admin Endpoints (4/4)

**Test 4: GET /api/billing/webhooks/failed**
- Endpoint: /api/billing/webhooks/failed
- Method: GET
- Auth: JWT Bearer Token
- Expected Status: 200
- Parameters: page, size
- Response Type: PagedResponse<WebhookEvent>
- Tenant Scoped: YES
- Status: READY FOR TESTING

**Test 5: GET /api/billing/webhooks/failed/permanent**
- Endpoint: /api/billing/webhooks/failed/permanent
- Method: GET
- Auth: JWT Bearer Token
- Expected Status: 200
- Parameters: page, size
- Response Type: PagedResponse<WebhookEvent>
- Criteria: Max retries exceeded (>= 5)
- Tenant Scoped: YES
- Status: READY FOR TESTING

**Test 6: GET /api/billing/webhooks/failed/recent**
- Endpoint: /api/billing/webhooks/failed/recent
- Method: GET
- Auth: JWT Bearer Token
- Expected Status: 200
- Parameters: None
- Response Type: List<WebhookEvent>
- Time Range: Last 24 hours
- Tenant Scoped: YES
- Status: READY FOR TESTING

**Test 7: GET /api/billing/webhooks/stats**
- Endpoint: /api/billing/webhooks/stats
- Method: GET
- Auth: JWT Bearer Token
- Expected Status: 200
- Parameters: None
- Response Type: WebhookStatistics
- Metrics:
  - totalProcessed: int
  - totalFailed: int
  - pendingRetry: int
  - successRate: double
- Tenant Scoped: YES
- Status: READY FOR TESTING

---

### Admin Dashboard Endpoints (4/4)

**Test 8: GET /api/v1/admin/billing/webhook-events**
- Endpoint: /api/v1/admin/billing/webhook-events
- Method: GET
- Auth: JWT Bearer Token (ADMIN role)
- Expected Status: 200
- Parameters: page, size
- Response Type: PagedResponse<WebhookEventDTO>
- Scope: ALL webhooks (not tenant-scoped)
- Role Required: ADMIN
- Status: READY FOR TESTING

**Test 9: GET /api/v1/admin/billing/webhook-stats**
- Endpoint: /api/v1/admin/billing/webhook-stats
- Method: GET
- Auth: JWT Bearer Token (ADMIN role)
- Expected Status: 200
- Parameters: None
- Response Type: AdminWebhookStatistics
- Metrics:
  - totalEvents: int
  - successfulEvents: int
  - failedEvents: int
  - successRate: double
  - avgRetryCount: double
  - avgRetryTime: long (ms)
  - oldestFailure: DateTime
  - newestFailure: DateTime
- Scope: ALL webhooks (not tenant-scoped)
- Role Required: ADMIN
- Status: READY FOR TESTING

**Test 10: GET /api/v1/admin/billing/webhook-events/{eventId}**
- Endpoint: /api/v1/admin/billing/webhook-events/{eventId}
- Method: GET
- Auth: JWT Bearer Token (ADMIN role)
- Expected Status: 200
- Response Type: WebhookEventDetailDTO
- Scope: Specific event (no tenant restriction)
- Role Required: ADMIN
- Error: 404 if not found
- Status: REQUIRES SETUP (need failed webhook in DB)

**Test 11: PUT /api/v1/admin/billing/webhook-events/{eventId}/retry**
- Endpoint: /api/v1/admin/billing/webhook-events/{eventId}/retry
- Method: PUT
- Auth: JWT Bearer Token (ADMIN role)
- Expected Status: 200 (or 202)
- Response: Retry initiated message
- Scope: Specific event (no tenant restriction)
- Role Required: ADMIN
- Error: 404 if not found, 409 if already retrying
- Status: REQUIRES SETUP (need failed webhook in DB)

---

### Webhook Management Endpoints (2/2)

**Test 12: POST /api/billing/webhooks/{webhookId}/replay**
- Endpoint: /api/billing/webhooks/{webhookId}/replay
- Method: POST
- Auth: JWT Bearer Token
- Expected Status: 200 (or 202)
- Response: Replay initiated confirmation
- Tenant Scoped: YES (can only replay own webhooks)
- Error: 404 if not found, 403 if unauthorized
- Status: REQUIRES SETUP (need failed webhook in DB)

**Test 13: DELETE /api/billing/webhooks/{webhookId}**
- Endpoint: /api/billing/webhooks/{webhookId}
- Method: DELETE
- Auth: JWT Bearer Token (ADMIN role)
- Expected Status: 204 No Content
- Tenant Scoped: NO (admin can delete any)
- Error: 404 if not found, 403 if unauthorized
- Status: REQUIRES SETUP (need webhook in DB)

---

## Endpoint Summary Matrix

| # | Endpoint | Method | Auth Type | Status | Tests | Notes |
|---|----------|--------|-----------|--------|-------|-------|
| 1 | /stripe/webhook | POST | Signature | LIVE | 1 | External webhook |
| 2 | /webhooks/cakto | POST | Signature | LIVE | 1 | External webhook |
| 3 | /webhooks/sendgrid | POST | Bearer | LIVE | 1 | External webhook |
| 4 | /api/billing/webhooks/failed | GET | JWT | LIVE | 1 | Monitoring |
| 5 | /api/billing/webhooks/failed/permanent | GET | JWT | LIVE | 1 | Monitoring |
| 6 | /api/billing/webhooks/failed/recent | GET | JWT | LIVE | 1 | Monitoring |
| 7 | /api/billing/webhooks/stats | GET | JWT | LIVE | 1 | Analytics |
| 8 | /api/v1/admin/billing/webhook-events | GET | JWT+ADMIN | LIVE | 1 | Admin |
| 9 | /api/v1/admin/billing/webhook-stats | GET | JWT+ADMIN | LIVE | 1 | Admin |
| 10 | /api/v1/admin/billing/webhook-events/{id} | GET | JWT+ADMIN | READY | 0 | Requires data |
| 11 | /api/v1/admin/billing/webhook-events/{id}/retry | PUT | JWT+ADMIN | READY | 0 | Requires data |
| 12 | /api/billing/webhooks/{id}/replay | POST | JWT | READY | 0 | Requires data |
| 13 | /api/billing/webhooks/{id} | DELETE | JWT+ADMIN | READY | 0 | Requires data |

**TOTAL: 13/13 ENDPOINTS TESTABLE**
- LIVE TESTING: 9 endpoints (ready to test now)
- REQUIRES SETUP: 4 endpoints (need failed webhooks in DB first)

---

## Test Execution Flow

### Phase 1: Direct Ingestion Tests (3)
1. Register test user and authenticate
2. Send test events to each webhook endpoint
3. Verify acceptance with proper signatures
4. Validate event storage

### Phase 2: Monitoring Tests (4)
1. Query failed webhooks list
2. Query permanent failures
3. Query recent failures
4. Retrieve statistics

### Phase 3: Admin Tests (2)
1. Query all webhook events (admin view)
2. Retrieve admin statistics

### Phase 4: Setup Data Generation
1. Create failed webhook records in DB (for tests 10-13)
2. Ensure test webhooks have:
   - Failed status (HTTP 5xx response)
   - Valid timestamps
   - Retry count > 0

### Phase 5: Replay Tests (4)
1. Query specific webhook event
2. Retry specific event
3. Replay webhook
4. Delete webhook record

---

## Data Requirements

### For Webhook Ingestion (Tests 1-3)
- None (stateless endpoints)

### For Monitoring (Tests 4-7)
- None initially (can test with empty results)
- Optional: Create failed webhooks to populate

### For Admin (Tests 8-9)
- None initially (can test with empty results)

### For Replay (Tests 10-13)
- Prerequisites:
  - Failed webhook events in database
  - Events with status = FAILED
  - Last attempt must be recent enough
  - Retry count < max retry limit

### Setup Script to Generate Failed Webhooks
```sql
INSERT INTO webhook_events (
    id, tenant_id, webhook_type, event_id, payload,
    response_status, response_body, retry_count, next_retry_at,
    last_attempted_at, created_at, updated_at, deleted_at
) VALUES (
    gen_random_uuid(),
    (SELECT id FROM tenants LIMIT 1),
    'STRIPE',
    'evt_test_12345',
    '{"event": "charge.succeeded"}',
    500,
    'Internal Server Error',
    3,
    NOW() + INTERVAL '1 hour',
    NOW() - INTERVAL '1 hour',
    NOW(),
    NOW(),
    NULL
);
```

---

## Security Test Coverage

**Test: Invalid Stripe Signature**
- Expected: 401 Unauthorized
- Payload: Valid JSON, Invalid signature
- Purpose: Verify signature validation works

**Test: Missing Auth Header**
- Expected: 401 Unauthorized
- Payload: Valid event, no auth
- Purpose: Verify auth enforcement

**Test: Cross-Tenant Access**
- Expected: 403 Forbidden
- Method: Try to access other tenant's webhooks
- Purpose: Verify tenant isolation

**Test: Insufficient Privileges**
- Expected: 403 Forbidden
- Method: Non-admin user accessing admin endpoints
- Purpose: Verify role-based access control

---

## Performance Benchmarks

Target response times:
- Webhook ingestion: < 500ms
- List operations: < 1000ms
- Admin queries: < 2000ms
- Replay operation: < 500ms

---

## Known Limitations

1. Replay tests (10-13) require database setup
2. Signature verification depends on configured secrets
3. Admin endpoints require ADMIN role assignment
4. Statistics are cumulative (reset requires admin action)

---

## Success Criteria

**Pass condition: 100%** (13/13 tests passing)

### Immediate (9 tests)
- All 3 webhook ingestion endpoints operational
- All 4 monitoring endpoints returning data
- Both admin endpoints accessible

### After setup (4 tests)
- All replay/management endpoints working
- Cross-tenant security enforced
- Admin operations verified

---

## File References

Test Script: `test-webhooks-complete.ps1`
Guide: `WEBHOOK_TEST_GUIDE.md`
This Checklist: `WEBHOOK_ENDPOINTS_CHECKLIST.md`
Expected Results: `WEBHOOK_TEST_RESULTS.md` (generated after run)

---

## Next Actions

1. Execute test-webhooks-complete.ps1
2. Review results in WEBHOOK_TEST_RESULTS.md
3. If 9/13 pass: Create failed webhook test data
4. Re-run tests to get 13/13
5. Document any failures and remediate
6. Commit results to version control
