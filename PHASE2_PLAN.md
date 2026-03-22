# 🚀 PHASE 2: Multi-Tenant Webhook Isolation + Save-Before-Process

**Status:** Planning Complete  
**Date:** March 22, 2026  
**Branch:** conclusao-dos-erros  
**Commit Marker:** f4a034f (Phase 1 Complete)  

---

## 📋 Executive Summary

Phase 2 adiciona **isolamento multi-tenant** aos webhooks e implementa **save-before-process** para garantir idempotência e auditoria.

### Key Objectives:
1. ✅ Adicionar campo `tenantId` ao StripeEventLog (DB migration)
2. ✅ Extrair `tenant_id` do metadados do Stripe Customer
3. ✅ Reordenar fluxo para SALVAR → PROCESSAR (não vice-versa)
4. ✅ Restaurar TenantContext antes de processar cada evento
5. ✅ Testes multi-tenant (2 tenants, sem vazamento de dados)

---

## 🗂️ ETAPA 1: Database Migration - Adicionar tenantId

### Current State:
```sql
stripe_event_logs (
  id, event_id, event_type, payload, status,
  retry_count, max_retries, next_retry_at,
  last_error, processed_at, created_at, updated_at
)
-- ❌ FALTA: tenant_id, customer_id
```

### Target State:
```sql
stripe_event_logs (
  id, event_id, event_type, payload, status,
  retry_count, max_retries, next_retry_at,
  last_error, processed_at, created_at, updated_at,
  ✅ tenant_id UUID DEFAULT NULL,
  ✅ customer_id VARCHAR(100) DEFAULT 'unknown',
)
-- Indices:
-- idx_tenant_id (tenant_id)  -- Para isolamento
-- idx_tenant_status (tenant_id, status)  -- Para queries por tenant
-- idx_customer_id (customer_id)  -- Para rastreabilidade
```

### Tasks:
1. [ ] Criar migration SQL (flyway: V3__add_tenant_to_webhook.sql)
2. [ ] Adicionar fields ao StripeEventLog.java
3. [ ] Adicionar @Index annotations
4. [ ] Atualizar queries do repository

---

## 🔗 ETAPA 2: Extrair tenant_id do Stripe Customer

### Strategy:

```
Webhook Event → Stripe Customer ID → Stripe API Call → Get Customer Metadata
                                     ↓
                           metadata["tenant_id"]
                                     ↓
                           StripeEventLog.tenantId
```

### Implementation:

**Arquivo:** `StripeService.java`

```java
/**
 * Extract tenant ID from Stripe Event
 * - Busca o Customer via ID extraído do evento
 * - Recupera metadata["tenant_id"] da API
 * - Fallback: "unknown" se não encontrar
 */
public String extractTenantIdFromEvent(Event event) {
    try {
        String customerId = extractCustomerIdFromEvent(event);
        if ("unknown".equals(customerId)) {
            return "unknown";
        }
        
        // Fetch customer from Stripe to get metadata
        Customer customer = Customer.retrieve(customerId);
        Map<String, String> metadata = customer.getMetadata();
        
        if (metadata != null && metadata.containsKey("tenant_id")) {
            return metadata.get("tenant_id");
        }
        
        return "unknown";
    } catch (StripeException e) {
        log.warn("Failed to extract tenant from event {}: {}", event.getId(), e.getMessage());
        return "unknown";
    }
}
```

### Tasks:
1. [ ] Implementar `extractTenantIdFromEvent()` em StripeService
2. [ ] Add logging para rastreabilidade
3. [ ] Testar com Stripe Test API

---

## 💾 ETAPA 3: Save-Before-Process Pattern

### Current Flow (UNSAFE):
```
POST /stripe/webhook
  ↓
1. Validar Signature
  ↓
2. Processar evento (pode falhar)
  ↓
3. Salvar na DB (talvez não chegue aqui)
  ↓
200 OK ou erro
```

**Problema:** Se falhar em (2), evento é perdido ou fica duplicado

### New Flow (SAFE):
```
POST /stripe/webhook
  ↓
1. Validar Signature
  ↓
2. Extrair tenant_id do evento
  ↓
3. SALVAR StripeEventLog (status: PENDING) ✅ GARANTIDO
  ↓
4. Dentro try-catch:
   - Restaurar TenantContext(tenant_id)
   - Processar evento completo
   - Atualizar status: SUCCESS
  ↓
5. On error:
   - Atualizar status: RETRY_PENDING
   - Schedule retry via @Scheduled
  ↓
200 OK (sempre)
```

### Code Changes - StripeWebhookController.java:

```java
@PostMapping("/webhook")
public ResponseEntity<String> handleWebhook(
    @RequestBody String payload,
    @RequestHeader("Stripe-Signature") String signature
) {
    long startTime = System.currentTimeMillis();
    
    try {
        // 1️⃣ Validar Signature
        Event event = stripeService.validateWebhookSignature(payload, signature);
        String customerId = extractCustomerIdFromEvent(event);
        
        // 2️⃣ Extrair tenant_id
        String tenantId = stripeService.extractTenantIdFromEvent(event);
        
        // 3️⃣ SALVAR ANTES (status: PENDING)
        StripeEventLog eventLog = StripeEventLog.builder()
            .eventId(event.getId())
            .eventType(event.getType())
            .customerId(customerId)
            .tenantId(tenantId != null ? UUID.fromString(tenantId) : null)
            .payload(payload)
            .status(PENDING)
            .retryCount(0)
            .maxRetries(3)
            .build();
        
        StripeEventLog saved = stripeEventLogRepository.save(eventLog);
        long processingTimeMs = System.currentTimeMillis() - startTime;
        webhookLoggingService.logWebhookReceived(
            event, true, customerId, processingTimeMs
        );
        
        // 4️⃣ PROCESSAR dentro try-catch
        try {
            // Restaurar TenantContext
            TenantContext.setCurrentTenant(UUID.fromString(tenantId));
            
            // Checar duplicação (via eventId)
            boolean isDuplicate = isEventIdempotent(event.getId());
            if (isDuplicate) {
                saved.setStatus(SUCCESS);
                stripeEventLogRepository.save(saved);
                webhookLoggingService.logWebhookProcessed(
                    event, customerId, true
                );
                return ResponseEntity.ok("{}");
            }
            
            // Processar evento
            processStripeEvent(event);
            
            // Atualizar status: SUCCESS
            saved.setStatus(SUCCESS);
            saved.setProcessedAt(LocalDateTime.now());
            stripeEventLogRepository.save(saved);
            
            webhookLoggingService.logWebhookProcessed(
                event, customerId, false
            );
        } catch (Exception e) {
            // 5️⃣ On error: schedule retry
            saved.setStatus(RETRY_PENDING);
            saved.setLastError(e.getMessage());
            saved.setRetryCount(0);
            saved.setNextRetryAt(calculateNextRetry(0));
            stripeEventLogRepository.save(saved);
            
            webhookLoggingService.logWebhookFailed(
                event, customerId, e.getMessage()
            );
        } finally {
            TenantContext.clear();
        }
        
        return ResponseEntity.ok("{}");
        
    } catch (SignatureVerificationException e) {
        webhookLoggingService.logSignatureValidationFailure(
            null, e.getMessage()
        );
        return ResponseEntity.status(401).body("Signature verification failed");
    }
}
```

### Tasks:
1. [ ] Refatorar StripeWebhookController (reorder logic)
2. [ ] Add tenantId extraction
3. [ ] Add save-before-process
4. [ ] Add TenantContext.setCurrentTenant() + finally block
5. [ ] Update retry scheduler para usar tenantId

---

## 🧪 ETAPA 4: Testes Multi-Tenant

### Test Scenario:

```
Tenant A (UUID: 550e8400-e29b-41d4-a716-446655440000)
├─ Customer: cus_A1
├─ Webhook Event 1: evt_A1 (charge.succeeded)
└─ Expected: StripeEventLog.tenantId = Tenant A

Tenant B (UUID: 550e8400-e29b-41d4-a716-446655440001)
├─ Customer: cus_B1
├─ Webhook Event 2: evt_B1 (charge.succeeded)
└─ Expected: StripeEventLog.tenantId = Tenant B

Validation:
✅ No data leak: A's events don't appear in B's queries
✅ Retry scheduler respects tenant isolation
✅ Admin queries filtered by tenant
✅ Logs show correct tenant_id
```

### Test Files to Create:

1. **MultiTenantWebhookTest.java**
   - Simulate webhooks from 2 different tenants
   - Verify isolation in StripeEventLog
   - Check retry scheduler respects tenant boundaries

2. **WebhookTenantContextTest.java**
   - Verify TenantContext set/clear on webhook processing
   - Test context leakage (should be zero)
   - Test parallel webhook processing from different tenants

### Tasks:
1. [ ] Create MultiTenantWebhookTest
2. [ ] Create WebhookTenantContextTest
3. [ ] Add test data fixtures (Tenant A, B, Customers, Webhooks)
4. [ ] Run full test suite (target: 15+ passing tests)

---

## 📦 ETAPA 5: Integração ao Retry Scheduler

### Changes to StripeEventRetryScheduler.java:

```java
@Scheduled(fixedDelay = 60000, initialDelay = 10000)
public void processFailedEvents() {
    // ✅ Agora filtra por tenant também
    List<StripeEventLog> failedEvents = 
        stripeEventLogRepository.findPendingRetriesByTenant(
            RETRY_PENDING,
            getCurrentTenantId()  // ← ADD: Tenant isolation
        );
    
    for (StripeEventLog event : failedEvents) {
        try {
            // ✅ Set TenantContext before processing
            TenantContext.setCurrentTenant(event.getTenantId());
            
            // Retry logic...
            
        } finally {
            TenantContext.clear();
        }
    }
}
```

### Tasks:
1. [ ] Update StripeEventLogRepository queries (add tenantId filter)
2. [ ] Update StripeEventRetryScheduler (set TenantContext)
3. [ ] Test retry with multi-tenant scenarios

---

## 🚀 IMPLEMENTATION SEQUENCING

### Sprint Day 1 (Today):
- [ ] 1.1 - Create migration SQL file
- [ ] 1.2 - Update StripeEventLog entity
- [ ] 1.3 - Update Repository queries

### Sprint Day 2 (Tomorrow):
- [ ] 2.1 - Implement extractTenantIdFromEvent()
- [ ] 2.2 - Refactor StripeWebhookController
- [ ] 2.3 - Compile & Fix Errors

### Sprint Day 3 (Next Day):
- [ ] 3.1 - Create MultiTenantWebhookTest
- [ ] 3.2 - Create WebhookTenantContextTest
- [ ] 3.3 - Run full test suite

### Sprint Day 4 (Final):
- [ ] 4.1 - Final validation
- [ ] 4.2 - Git commit Phase 2
- [ ] 4.3 - Prepare PR for review

---

## ✅ Definition of Done (Phase 2)

**Code Quality:**
- [ ] All compilation errors resolved (mvn clean package exit 0)
- [ ] No deprecated API usage
- [ ] Proper null safety checks

**Testing:**
- [ ] 15+ tests passing (100% pass rate)
- [ ] MultiTenantWebhookTest passes
- [ ] WebhookTenantContextTest passes
- [ ] TenantIsolationTest still passes

**Functionality:**
- [ ] ✅ tenant_id stored in StripeEventLog
- [ ] ✅ tenant_id extracted from Stripe Customer
- [ ] ✅ Save-before-process pattern working
- [ ] ✅ TenantContext restored before processing
- [ ] ✅ Retry scheduler respects tenant isolation

**Logging & Monitoring:**
- [ ] ✅ JSON logs show tenant_id
- [ ] ✅ Admin queries filtered by tenant
- [ ] ✅ No data leaks in queries

**Documentation:**
- [ ] [ ] Phase 2 Implementation Summary
- [ ] [ ] Migration SQL documented
- [ ] [ ] Test scenarios documented

---

## 📞 Questions Before Starting:

1. **Stripe Metadata**: Vamos adicionar `tenant_id` na criação de Customer?
2. **Fallback Strategy**: Se não conseguir extrair tenant_id → usar "unknown"?
3. **Admin Queries**: Admin pode ver webhooks de TODOS os tenants?
4. **Retry Scheduler**: Deve processar TODOS os tenants ou um por um?

---

## 🔗 Related Files

- Phase 1 Summary: `PHASE1_COMPLETE.md` (commit f4a034f)
- Migration SQL: `src/main/resources/db/migration/V3__add_tenant_to_webhook.sql` (to create)
- Tests: `src/test/java/com/leadflow/backend/webhook/MultiTenantWebhookTest.java` (to create)
- Implementation: `src/main/java/com/leadflow/backend/controller/StripeWebhookController.java` (to update)

---

**Ready to start ETAPA 1 (Database Migration)?** 🚀
