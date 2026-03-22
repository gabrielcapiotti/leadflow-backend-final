# Phase 3: Webhooks Avançados, Alertas e Observabilidade

**Objetivo:** Implementar sistema robusto de alertas, dashboard de monitoramento e observabilidade completa para webhooks Stripe.

**Status:** Planejado | **Início:** 22/03/2026 | **Branch:** conclusao-dos-erros

---

## 📋 Resumo Executivo

A Fase 3 complementa as Fases 1-2 adicionando:
- ✅ Circuit Breaker para evitar retry infinito
- ✅ Dashboard de status de webhooks (QueryDSL)
- ✅ Sistema de alertas configurável
- ✅ Métricas e observabilidade estruturada
- ✅ Análise de falhas com rastreabilidade
- ✅ Testes de carga e resiliência

---

## 🎯 ETAPA 1: Circuit Breaker para Retry Scheduler

**Objetivo:** Evitar retry infinito em falhas permanentes. Implementar padrão Circuit Breaker.

### Arquitetura:

```
Event fails (max retries)
        ↓
Check circuit state
        ↓
CLOSED (Normal) → Set to FAILED, increment failure counter
        ↓
If failures > threshold (e.g., 10) → OPEN circuit
        ↓
OPEN (Circuit Aberto) → Reject new retries, emit alert
        ↓
After cooldown (5 min) → HALF_OPEN (test mode)
        ↓
HALF_OPEN (Testing) → Allow limited retries
        ↓
If success → CLOSED, else → stay OPEN
```

### Arquivos a criar/modificar:

**criar:** `CircuitBreakerConfig.java`
- Estados: CLOSED, OPEN, HALF_OPEN
- Threshold: 10 falhas consecutivas
- Timeout: 5 minutos (cooldown)
- Métricas: contador de falhas, estado atual

**modificar:** `StripeEventRetryScheduler`
- Injetar CircuitBreakerConfig
- Check estado antes de retry
- UPDATE circuit state baseado em resultado

**criar:** `CircuitBreakerMetrics.java`
- EventListener para mudanças de estado
- Log estruturado quando OPEN/CLOSED
- Emit evento para alertas

### Testes:

**criar:** `CircuitBreakerTest.java`
- ✅ testCircuitOpenAfterThreshold
- ✅ testHalfOpenAftersTimeout
- ✅ testRecoverAfterSuccess
- ✅ testRejectRetryWhenOpen

---

## 🎯 ETAPA 2: Dashboard de Webhooks (QueryDSL)

**Objetivo:** Endpoint REST para visualizar status de webhooks com filtros avançados.

### Endpoints:

```
GET /api/admin/webhooks/dashboard
- Auth: ADMIN only
- Response: {
    total: 1024,
    pending: 45,
    succeeded: 956,
    failed: 23,
    retryPending: 12,
    byTenant: [{
      tenantId: "...",
      count: 150,
      lastEvent: "2026-03-22T10:30:00Z"
    }],
    byEventType: [{
      eventType: "charge.succeeded",
      count: 512,
      successRate: "98.5%",
      avgProcessingTime: "234ms"
    }]
  }

GET /api/admin/webhooks/search?tenant={id}&status={status}&type={type}&page=0
- QueryDSL filter
- Pagination with sorting
- Response: List<WebhookDTO>

GET /api/admin/webhooks/{id}/history
- Event processing history
- All retry attempts
- Error stack traces
```

### Arquivos a criar/modificar:

**criar:** `WebhookDashboardController.java`
- GET `/api/admin/webhooks/dashboard`
- GET `/api/admin/webhooks/search`
- GET `/api/admin/webhooks/{id}/history`

**criar:** `WebhookSpecification.java`
- QueryDSL onde com filtros (tenant, status, eventType, dateRange)

**criar:** `WebhookDTO.java`
- DTO para resposta
- Inclui: eventId, tenantId, status, eventType, retryCount, createdAt, processedAt, lastError

**modificar:** `StripeEventLog`
- Adicionar índices para queries rápidas

### Testes:

**criar:** `WebhookDashboardTest.java`
- ✅ testGetDashboard
- ✅ testSearchByStatus
- ✅ testPaginationAndSorting
- ✅ testAdminAuthRequired

---

## 🎯 ETAPA 3: Sistema de Alertas

**Objetivo:** Notificar admins quando webhooks falham ou circuit breaker abre.

### Eventos de alerta:

1. **WEBHOOK_RETRY_FAILED** - Falha após max retries
2. **CIRCUIT_BREAKER_OPENED** - Circuit aberto (múltiplas falhas)
3. **TENANT_WEBHOOK_RATE_HIGH** - Taxa de erros > 20% para tenant
4. **PROCESSING_TIME_EXCEEDS** - Processamento > 5 segundos

### Arquivos a criar:

**criar:** `WebhookAlertEvent.java`
```java
record WebhookAlertEvent(
    String alertType,        // WEBHOOK_RETRY_FAILED, CIRCUIT_BREAKER_OPENED
    UUID tenantId,
    String eventId,
    String message,
    Map<String, Object> context,
    LocalDateTime timestamp
) {}
```

**criar:** `WebhookAlertService.java`
```java
public void emitAlert(WebhookAlertEvent event) {
  // 1. Save to DB (alerts table)
  // 2. Publish ApplicationEvent for listeners
  // 3. Send to external service (email, Slack, etc.)
}
```

**criar:** `WebhookAlertListener.java`
```java
@EventListener
public void onWebhookAlert(WebhookAlertEvent event) {
  if (event.alertType().equals("CIRCUIT_BREAKER_OPENED")) {
    sendEmailToAdmins("Circuit breaker opened: " + event.message());
    logCritical(event);
  }
}
```

**criar:** `WebhookAlert` entity
- alertType, tenantId, eventId, message, acknowledged, acknowledgedBy

### Testes:

**criar:** `WebhookAlertTest.java`
- ✅ testAlertEmittedOnRetryFailure
- ✅ testAlertEmittedOnCircuitOpen
- ✅ testAlertNotEmittedForSuccessfulEvent

---

## 🎯 ETAPA 4: Observabilidade Avançada

**Objetivo:** Structured logging, métricas, e potencial para OpenTelemetry.

### Melhorias ao WebhookLoggingService:

**modificar:** `WebhookLoggingService`
- Adicionar field `circuitBreakerState` aos logs
- Adicionar field `tenantId` aos logs
- Adicionar field `processingTimeMs`
- Adicionar field `retryAttempt`

Exemplo de log:
```json
{
  "timestamp": "2026-03-22T10:30:45Z",
  "level": "INFO",
  "message": "Webhook retry succeeded",
  "eventId": "evt_1HqJXnGv...",
  "tenantId": "550e8400-e29b-41d4...",
  "eventType": "charge.succeeded",
  "status": "SUCCESS",
  "retryCount": 2,
  "processingTime": 234,
  "circuitBreakerState": "CLOSED",
  "customerEmail": "customer@example.com"
}
```

**criar:** `WebhookMetricsService.java`
- Micrometer gauges para: total events, pending, failed
- Rastreamento de tempo de processamento (percentis 50/90/99)
- Taxa de sucesso por tenant

### Métricas:

```
webhook.events.total (Counter)
webhook.events.pending (Gauge)
webhook.events.failed (Gauge)
webhook.events.succeeded (Gauge)
webhook.processing.time (Timer)
  - min, max, avg, p50, p90, p99
webhook.retry.attempts (Histogram)
webhook.circuit.breaker.state (Gauge)
  - 0=CLOSED, 1=OPEN, 2=HALF_OPEN
```

### Testes:

**criar:** `WebhookMetricsTest.java`
- ✅ testMetricsEmitted
- ✅ testProcessingTimeTracked
- ✅ testCircuitBreakerMetrics

---

## 🎯 ETAPA 5: Análise de Falhas Detalhada

**Objetivo:** Rastreabilidade completa do motivo de cada falha, com sugestões de fix.

### Arquivos a criar/modificar:

**criar:** `WebhookFailureAnalysis` entity
```java
@Entity
private String eventId;
private UUID tenantId;
private String failureType;      // TIMEOUT, JSON_ERROR, BUSINESS_LOGIC, etc.
private String rootCause;        // Stack trace resumida
private String suggestion;       // Sugestão de fix
private LocalDateTime createdAt;
```

**modificar:** `StripeWebhookController`
- Catch exceptions e classify failure type
- Save to WebhookFailureAnalysis
- Attempt automatic recovery suggestions

**criar:** `FailureClassifier.java`
```java
// Classificar exceções:
if (exception instanceof JsonProcessingException) {
  return FAILURE_TYPE.JSON_ERROR;
} else if (exception instanceof TimeoutException) {
  return FAILURE_TYPE.TIMEOUT;
} else if (exception instanceof StripeAPIException) {
  return FAILURE_TYPE.STRIPE_API_ERROR;
} // ... etc
```

### Sugestões automáticas:

```java
if (failureType == JSON_ERROR) {
  suggestion = "Payload JSON inválido. Verificar formato com Stripe docs.";
} else if (failureType == TIMEOUT) {
  suggestion = "Timeout ao chamar API externa. Verificar latência de rede.";
} // ... etc
```

### Testes:

**criar:** `WebhookFailureAnalysisTest.java`
- ✅ testFailureTypeClassification
- ✅ testSuggestionGeneration
- ✅ testFailurePersisted

---

## 🎯 ETAPA 6: Testes de Carga e Resiliência

**Objetivo:** Validar comportamento sob stress (1000s de eventos concorrentes).

### Testes:

**criar:** `WebhookPerformanceTest.java` (JUnit + JMH opcional)

```java
@Test
void testProcessing1000EventsConcurrently() {
  // Simular 1000 eventos simultâneos
  // Verify: todos processados/retried
  // Verify: circuit breaker não abre
  // Verify: sem memory leak
}

@Test
void testCircuitBreakerUnderHighFailureRate() {
  // Simular 95% de taxa de erro
  // Verify: circuit breaker abre após threshold
  // Verify: alertas emitidos
  // Verify: sistema não fica em deadlock
}
```

**criar:** `WebhookStressScenario.java`
- Spike test: 10x aumento súbito
- Soak test: execução prolongada
- Gradual ramp-up: aumento gradual

---

## 📊 Timeline

| Etapa | Tarefa | Duração | Dependências |
|-------|--------|---------|--------------|
| 1 | Circuit Breaker | 1-2h | Phase 2 ✅ |
| 2 | Dashboard | 1-2h | ETAPA 1 |
| 3 | Alertas | 1-2h | ETAPA 1-2 |
| 4 | Observabilidade | 1h | ETAPA 1-3 |
| 5 | Análise de Falhas | 1-2h | ETAPA 1-4 |
| 6 | Testes de Carga | 1-2h | ETAPA 1-5 |
| **Total** | | **6-11h** | |

---

## 🎬 Checklist de Execução

- [ ] ETAPA 1: Circuit Breaker implementation + tests
- [ ] ETAPA 2: Dashboard endpoints + QueryDSL + tests
- [ ] ETAPA 3: Alert events + listeners + tests
- [ ] ETAPA 4: Advanced logging + metrics + tests
- [ ] ETAPA 5: Failure analysis + classification + tests
- [ ] ETAPA 6: Performance + stress tests
- [ ] Documentation: Phase 3 Summary
- [ ] Git commit: "feat: Phase 3 complete - webhooks avançados"
- [ ] Code review & merge to master

---

## 📌 Notas

- **Circuit Breaker threshold:** 10 falhas consecutivas (ajustável)
- **Circuit Breaker timeout:** 5 minutos (ajustável)
- **Dashboard:** Apenas ADMIN access (verificar @PreAuthorize)
- **Alertas:** Usar Spring ApplicationEvent pattern
- **Métricas:** Integrar com Micrometer (Spring Boot default)
- **Performance:** Adicionar índices no DB conforme necessário

---

**Próximos passos:** Começar com ETAPA 1 (Circuit Breaker)?
