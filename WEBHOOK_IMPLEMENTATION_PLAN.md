# 🚀 WEBHOOK SYSTEM - PLANO DE IMPLEMENTAÇÃO

**Versão:** 1.0 | **Data:** 22/03/2026 | **Status:** PRONTO PARA EXECUÇÃO

---

## FASE 1 - CRÍTICA (1-2 Sprints)

### ✅ Tarefa 1.1: Implementar Backoff Exponencial

**Arquivo:** `StripeEventRetryService.java` (novo)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class StripeEventRetryService {

    private final StripeEventLogRepository eventLogRepository;
    private final StripeWebhookProcessor webhookProcessor;
    private final TenantContextService tenantContextService;

    private static final long INITIAL_DELAY_MS = 1000;      // 1 segundo
    private static final long MAX_DELAY_MS = 300000;        // 5 minutos
    private static final int MAX_RETRIES = 3;
    private static final double BACKOFF_MULTIPLIER = 2.0;

    /**
     * Calcula delay em ms baseado no número de tentativas (exponential backoff)
     * Tentativa 1: 1s
     * Tentativa 2: 2s
     * Tentativa 3: 4s
     * Tentativa 4: 8s (etc, capped em 5 min)
     */
    private long calculateBackoffDelay(int retryCount) {
        long delay = (long)(INITIAL_DELAY_MS * Math.pow(BACKOFF_MULTIPLIER, retryCount));
        return Math.min(delay, MAX_DELAY_MS);
    }

    /**
     * Agenda webhook para retry baseado em exponential backoff
     */
    public void scheduleRetry(StripeEventLog eventLog) {
        if (eventLog.getRetryCount() >= MAX_RETRIES) {
            eventLog.setStatus(EventProcessingStatus.FAILED_PERMANENT);
            log.error("Max retries exceeded for webhook {}, marking as permanent failure", 
                eventLog.getEventId());
            eventLogRepository.save(eventLog);
            return;
        }

        long nextRetryMs = calculateBackoffDelay(eventLog.getRetryCount());
        LocalDateTime nextRetryAt = LocalDateTime.now().plusNanos(nextRetryMs * 1_000_000);

        eventLog.setStatus(EventProcessingStatus.RETRY_SCHEDULED);
        eventLog.setNextRetryAt(nextRetryAt);
        eventLogRepository.save(eventLog);

        log.info("Webhook {} scheduled for retry at {} (attempt {}/{})", 
            eventLog.getEventId(), nextRetryAt, 
            eventLog.getRetryCount() + 1, MAX_RETRIES);
    }

    /**
     * Job scheduler - executado a cada 60 segundos
     * Processa webhooks que atingiram seu tempo de retry
     */
    @Scheduled(fixedDelay = 60000) // 60 segundos
    public void processScheduledRetries() {
        log.debug("Starting webhook retry job");

        List<StripeEventLog> duedRetries = eventLogRepository
            .findByStatusAndNextRetryAtBefore(
                EventProcessingStatus.RETRY_SCHEDULED,
                LocalDateTime.now()
            );

        if (duedRetries.isEmpty()) {
            log.debug("No webhooks ready for retry");
            return;
        }

        log.info("Found {} webhooks ready for retry", duedRetries.size());

        for (StripeEventLog eventLog : duedRetries) {
            try {
                retryWebhookEvent(eventLog);
            } catch (Exception e) {
                log.error("Failed to retry webhook {}: {}", 
                    eventLog.getEventId(), e.getMessage());
            }
        }
    }

    /**
     * Retry individual de um webhook
     */
    private void retryWebhookEvent(StripeEventLog eventLog) {
        try {
            // Restaura contexto de tenant se disponível
            if (eventLog.getTenantId() != null) {
                TenantContext.setCurrentTenant(eventLog.getTenantId());
            }

            // Reconstrói evento do payload JSON
            Event event = Jackson.deserialize(eventLog.getPayload(), Event.class);

            // Reprocessa
            webhookProcessor.process(event);

            // Marca como sucesso
            eventLog.setStatus(EventProcessingStatus.SUCCESS);
            eventLog.setProcessedAt(LocalDateTime.now());
            log.info("Successfully retried webhook {} on attempt {}", 
                eventLog.getEventId(), eventLog.getRetryCount() + 1);

        } catch (Exception e) {
            // Incrementa contador e agenda novo retry
            eventLog.setRetryCount(eventLog.getRetryCount() + 1);
            eventLog.setLastError(e.getMessage());

            if (eventLog.getRetryCount() < MAX_RETRIES) {
                scheduleRetry(eventLog);
            } else {
                eventLog.setStatus(EventProcessingStatus.FAILED_PERMANENT);
                log.error("Webhook {} failed permanently after {} retries", 
                    eventLog.getEventId(), MAX_RETRIES);
            }

        } finally {
            eventLogRepository.save(eventLog);
            TenantContext.clear();
        }
    }

    /**
     * API endpoint para retry manual (para dashboard)
     */
    @PostMapping("/api/billing/webhooks/{eventId}/manual-retry")
    public ResponseEntity<String> manualRetry(@PathVariable String eventId) {
        StripeEventLog eventLog = eventLogRepository.findByEventId(eventId)
            .orElseThrow(() -> new ResourceNotFoundException("Webhook not found"));

        if (eventLog.getRetryCount() >= MAX_RETRIES) {
            return ResponseEntity.status(409)
                .body("Max retries exceeded. Use force-retry for override");
        }

        retryWebhookEvent(eventLog);
        return ResponseEntity.ok("Retry scheduled");
    }
}
```

**Database Changes (Migration):**
```sql
ALTER TABLE stripe_event_log 
ADD COLUMN next_retry_at TIMESTAMP,
ADD COLUMN tenant_id VARCHAR,
ADD COLUMN status VARCHAR DEFAULT 'RECEIVED';

-- Alterar status enum para incluir novos estados
ALTER TABLE stripe_event_log 
DROP CONSTRAINT stripe_event_log_status_check;

ALTER TABLE stripe_event_log 
ADD CONSTRAINT stripe_event_log_status_check 
CHECK (status IN (
    'RECEIVED',          -- Webhook recebido
    'PROCESSING',        -- Sendo processado
    'SUCCESS',           -- Sucesso
    'FAILED',            -- Falha (retry programado)
    'RETRY_SCHEDULED',   -- Aguardando retry
    'FAILED_PERMANENT'   -- Falha permanente (Max retries)
));
```

---

### ✅ Tarefa 1.2: Logging Estruturado (JSON)

**Arquivo:** `WebhookLoggingService.java` (novo)

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class WebhookLoggingService {

    private final ObjectMapper objectMapper;

    /**
     * Estrutura de dados para logging JSON
     */
    @Data
    @Builder
    public static class WebhookLogEntry {
        private String eventId;
        private String eventType;
        private String customerId;
        private String tenantId;
        private String status;
        private Long processingTimeMs;
        private Boolean signatureValidated;
        private Boolean idempotencyCheck;
        private Integer retryCount;
        private String errorMessage;
        private LocalDateTime timestamp;
    }

    /**
     * Logar evento em JSON estruturado
     */
    public void logWebhookReceived(Event event, boolean signatureValid, 
                                   boolean isNewEvent, long processingTimeMs) {
        try {
            WebhookLogEntry entry = WebhookLogEntry.builder()
                .eventId(event.getId())
                .eventType(event.getType())
                .customerId(extractCustomerId(event))
                .tenantId(TenantContext.getCurrentTenant())
                .status("processed")
                .processingTimeMs(processingTimeMs)
                .signatureValidated(signatureValid)
                .idempotencyCheck(isNewEvent ? null : false) // null = novo, false = duplicado
                .retryCount(0)
                .timestamp(LocalDateTime.now())
                .build();

            String jsonLog = objectMapper.writeValueAsString(entry);
            log.info(jsonLog);  // Framework captura como JSON

        } catch (JsonProcessingException e) {
            log.error("Failed to serialize webhook log entry", e);
        }
    }

    /**
     * Logar falha
     */
    public void logWebhookFailure(StripeEventLog eventLog, Exception e, int retryCount) {
        try {
            WebhookLogEntry entry = WebhookLogEntry.builder()
                .eventId(eventLog.getEventId())
                .eventType(eventLog.getEventType())
                .tenantId(eventLog.getTenantId())
                .status("failed")
                .retryCount(retryCount)
                .errorMessage(e.getMessage())
                .timestamp(LocalDateTime.now())
                .build();

            log.error(objectMapper.writeValueAsString(entry));

        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize webhook error log", ex);
        }
    }

    private String extractCustomerId(Event event) {
        return event.getCustomer() != null ? 
            event.getCustomer() : 
            "unknown";
    }
}
```

**Integração no Controller:**
```java
@PostMapping("/webhook")
public ResponseEntity<String> handleWebhook(HttpServletRequest request) throws IOException {
    long startTime = System.currentTimeMillis();
    
    // ... resto do código ...
    
    try {
        Event event = stripeService.constructWebhookEvent(payload, signatureHeader);
        boolean isNew = !isEventAlreadyProcessed(event.getId());
        
        webhookProcessor.process(event);
        
        long processingTimeMs = System.currentTimeMillis() - startTime;
        webhookLoggingService.logWebhookReceived(event, true, isNew, processingTimeMs);
        
        return ResponseEntity.ok("received");
    } catch (Exception e) {
        webhookLoggingService.logWebhookFailure(eventLog, e, retryCount);
    }
}
```

---

## FASE 2 - IMPORTANTE (2-3 Sprints)

### 🎯 Tarefa 2.1: Multi-Tenant no Webhook

**Passo 1: Adicionar Metadata ao Stripe Customer**

```java
@Service
@RequiredArgsConstructor
public class StripeCustomerService {

    public Customer createCustomerWithTenant(String email, Long userId, String tenantId) {
        return Customer.create(new CustomerCreateParams.Builder()
            .setEmail(email)
            .setMetadata(Map.ofEntries(
                Map.entry("tenant_id", tenantId),
                Map.entry("user_id", userId.toString()),
                Map.entry("created_at", LocalDateTime.now().toString())
            ))
            .build());
    }
}
```

**Passo 2: Extrair Tenant do Webhook**

```java
private String extractTenantFromEvent(Event event) {
    // Stripe sempre fornece customer no event
    String stripeCustomerId = event.getCustomer();
    
    if (stripeCustomerId == null) {
        log.warn("Webhook {} has no customer", event.getId());
        return "public";  // Default fallback
    }

    // Recuperar customer do Stripe para ler metadata
    Customer customer = Customer.retrieve(stripeCustomerId);
    Map<String, String> metadata = customer.getMetadata();
    
    return metadata != null ? 
        metadata.get("tenant_id") : 
        "public";
}
```

**Passo 3: Restaurar Contexto de Tenant**

```java
@PostMapping("/webhook")
public ResponseEntity<String> handleWebhook(HttpServletRequest request) {
    try {
        Event event = stripeService.constructWebhookEvent(payload, signatureHeader);
        
        // ← NOVO: Extrair e restaurar tenant
        String tenantId = extractTenantFromEvent(event);
        TenantContext.setCurrentTenant(tenantId);
        
        webhookProcessor.process(event);  // Agora isolado por tenant ✅
        
        return ResponseEntity.ok("received");
    } finally {
        TenantContext.clear();
    }
}
```

---

### 🎯 Tarefa 2.2: Reorganizar Ordem de Operações

**Antes (⚠️ Risco):**
```java
// Se falhar, perde auditoria
webhookProcessor.process(event);
recordWebhookEvent(event, true);
```

**Depois (✅ Seguro):**
```java
// Salva ANTES, garante auditoria mesmo que falhe
StripeEventLog eventLog = new StripeEventLog();
eventLog.setEventId(event.getId());
eventLog.setStatus(EventProcessingStatus.RECEIVED);
eventLog.setPayload(event.toString());
eventLogRepository.save(eventLog);  // Transação 1 ✅

try {
    webhookProcessor.process(event);
    eventLog.setStatus(EventProcessingStatus.SUCCESS);
    eventLogRepository.save(eventLog);  // Transação 2
} catch (Exception e) {
    eventLog.setStatus(EventProcessingStatus.FAILED);
    eventLog.setLastError(e.getMessage());
    eventLogRepository.save(eventLog);  // Transação 3
    scheduleRetry(eventLog);
    throw e;
}
```

---

## FASE 3 - NICE-TO-HAVE (Futuro)

### 📊 Tarefa 3.1: Dashboard de Webhooks

**Endpoint:** `GET /api/v1/admin/billing/webhook-dashboard`

```json
{
  "summary": {
    "total_events_24h": 1547,
    "success_rate": 99.8,
    "pending_retries": 3,
    "permanent_failures": 1
  },
  "by_type": {
    "charge.succeeded": 1200,
    "charge.failed": 147,
    "payment_intent.succeeded": 200
  },
  "recent_failures": [
    {
      "event_id": "evt_xxx",
      "type": "charge.succeeded",
      "error": "Customer not found",
      "retries": 1,
      "next_retry": "2026-03-22T13:10:00Z"
    }
  ]
}
```

---

### 🚨 Tarefa 3.2: Alertas Automáticos

```java
@Service
@Scheduled(fixedDelay = 300000) // 5 minutos
public void checkWebhookHealth() {
    long failureCountLast24h = eventLogRepository
        .countByStatusAndCreatedAtAfter(
            EventProcessingStatus.FAILED_PERMANENT,
            LocalDateTime.now().minusHours(24)
        );

    if (failureCountLast24h > ALERT_THRESHOLD) {
        // Enviar email/Slack
        notificationService.alert(
            "Webhook failures exceeded: " + failureCountLast24h + "/1000"
        );
    }
}
```

---

## 📋 CHECKLIST DE IMPLEMENTAÇÃO

### FASE 1 (CRÍTICA)

- [ ] Criar `StripeEventRetryService.java`
- [ ] Adicionar campos `next_retry_at`, `tenant_id` em migration
- [ ] Criar `WebhookLoggingService.java`
- [ ] Integrar logging no controller
- [ ] Testar retry com backoff exponencial
- [ ] Testar logging JSON em ferramentas (ELK/Datadog)
- [ ] Atualizar testes (adicionar cenários de retry)

### FASE 2 (IMPORTANTE)

- [ ] Adicionar metadata ao Stripe customer creation
- [ ] Implementar `extractTenantFromEvent()`
- [ ] Restaurar `TenantContext` no webhook
- [ ] Reorganizar ordem de operações (salvar ANTES)
- [ ] Testar isolamento multi-tenant
- [ ] Testar falhas de processamento preservam auditoria

### FASE 3 (NICE-TO-HAVE)

- [ ] Criar dashboard endpoint
- [ ] Implementar alertas automáticos
- [ ] Adicionar gráficos em UI

---

## ✅ RESULTADO ESPERADO

**Após Fase 1:**
- Webhooks com retry automático + backoff
- Logging estruturado para análise
- Visibilidade em falhas

**Após Fase 2:**
- Webhooks isolados por tenant
- Auditoria garantida em todos os cenários
- Zero data leaks entre tenants

**Após Fase 3:**
- Dashboard completo
- Alertas proativas
- Confiança 100% em produção

---

**Status:** 🟢 PRONTO PARA IMPLEMENTAÇÃO
