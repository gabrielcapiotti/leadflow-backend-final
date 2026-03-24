# Phase 3 - ETAPA 3: Webhook Alerts & Notifications System

## 🎯 Objetivo
Implementar um sistema de alertas automático que notifica administradores sobre problemas críticos no processamento de webhooks Stripe.

## 📋 Requisitos

### Cenários de Alerta (Alert Triggers)
1. **CIRCUIT_BREAKER_OPENED** - Circuit breaker aberto (10+ falhas)
2. **HIGH_FAILURE_RATE** - Taxa de falha > 50% nos últimos 5 min
3. **PROCESSING_STALLED** - Nenhum webhook processado nos últimos 10 min
4. **EXCESSIVE_RETRIES** - Webhook com +5 tentativas de retry
5. **TIMEOUT_DETECTED** - Latência média > 5 segundos
6. **DATABASE_ERROR** - Erro ao salvar webhook event

### Informações do Alerta
```java
WebhookAlertEvent {
  alertType: AlertType          // CIRCUIT_BREAKER_OPENED, ...
  severity: AlertSeverity       // CRITICAL, WARNING, INFO
  tenantId: UUID                // Qual tenant afetado
  message: String               // Descrição humanizada
  metrics: AlertMetricsDTO      // Dados de contexto
  createdAt: LocalDateTime
  resolvedAt: LocalDateTime?    // Null = ainda ativo
}
```

### Níveis de Severidade
- **CRITICAL** (alertType = CIRCUIT_BREAKER_OPENED, HIGH_FAILURE_RATE)
- **WARNING** (alertType = EXCESSIVE_RETRIES, TIMEOUT_DETECTED)
- **INFO** (alertType = PROCESSING_STALLED)

## 🏗️ Arquitetura

### 1. WebhookAlertEvent (Event Model)
- Location: `src/main/java/com/leadflow/backend/entities/WebhookAlertEvent.java`
- Fields: alertType, severity, tenantId, message, metrics, createdAt, resolvedAt
- Entity: Mapped to DB table `webhook_alerts`
- Indices: (tenantId, createdAt), (severity, createdAt), (resolvedAt, status)

### 2. WebhookAlertRepository
- Location: `src/main/java/com/leadflow/backend/repository/WebhookAlertRepository.java`
- Methods:
  - `findByTenantIdAndSeverity(UUID, AlertSeverity)`
  - `findRecentByTenant(UUID, duration)` - Últimas 24h
  - `findActiveAlerts()` - Alerts com resolvedAt = null
  - `countBySeverityInLastHour(AlertSeverity)`

### 3. WebhookAlertService (Business Logic)
- Location: `src/main/java/com/leadflow/backend/service/billing/WebhookAlertService.java`
- Methods:
  - `checkCircuitBreakerStatus(CircuitBreakerConfig)` - Dispara CIRCUIT_BREAKER_OPENED
  - `checkFailureRate(long totalFailed, long totalProcessed)` - Dispara HIGH_FAILURE_RATE
  - `checkProcessingStalled(LocalDateTime lastProcessedAt)` - Dispara PROCESSING_STALLED
  - `checkExcessiveRetries(StripeEventLog event)` - Dispara EXCESSIVE_RETRIES se retries > 5
  - `checkLatency(double avgProcessingTimeMs)` - Dispara TIMEOUT_DETECTED se avg > 5000ms
  - `createAlert(AlertType, AlertSeverity, UUID tenantId, String message)` 
  - `resolveAlert(WebhookAlertEvent alert)` - Mark as resolved
  - `getActiveAlerts()` - Retorna alerts não resolvidos
  - `getAlertHistory(UUID tenantId, Duration period)` - Histórico de alerts

### 4. WebhookAlertListener (Event Listener)
- Location: `src/main/java/com/leadflow/backend/service/billing/WebhookAlertListener.java`
- Purpose: Listen to webhook processing events and trigger alerts
- Responsibilities:
  - Listen to `WebhookProcessedEvent` (Spring event)
  - Checks baseline conditions periodically (scheduled task)
  - Calls WebhookAlertService to create alerts
  - De-duplicates alerts (não criar 2x o mesmo alerta em 1 min)

### 5. WebhookAlertController (Admin Endpoints)
- Location: `src/main/java/com/leadflow/backend/controller/billing/WebhookAlertController.java`
- Endpoints:
  - `GET /api/v1/billing/webhooks/alerts` - All active alerts (ADMIN)
  - `GET /api/v1/billing/webhooks/alerts/{tenantId}` - Alerts by tenant (ADMIN)
  - `GET /api/v1/billing/webhooks/alerts/history` - Last 24h history (ADMIN)
  - `POST /api/v1/billing/webhooks/alerts/{alertId}/resolve` - Mark as resolved (ADMIN)
  - `GET /api/v1/billing/webhooks/alerts/stats` - Alert statistics (ADMIN)

### 6. WebhookAlertDTO (Response Model)
- Location: `src/main/java/com/leadflow/backend/dto/billing/WebhookAlertDTO.java`
- Purpose: API response model for alerts
- Fields:
  - alertId: UUID
  - alertType: AlertType (enum)
  - severity: AlertSeverity (enum)
  - tenantId: UUID
  - message: String
  - metrics: Map<String, Object>
  - createdAt: LocalDateTime
  - resolvedAt: LocalDateTime?
  - durationMinutes: Long (idade do alert se ativo)

## 📊 Database Migration
- **File:** `V10__add_webhook_alerts_table.sql`
- **Table:** `webhook_alerts`
- **Columns:**
  - id UUID PRIMARY KEY
  - alert_type VARCHAR(50) NOT NULL
  - severity VARCHAR(20) NOT NULL
  - tenant_id UUID NOT NULL
  - message TEXT
  - metrics JSONB
  - created_at TIMESTAMP NOT NULL
  - resolved_at TIMESTAMP NULL
  - updated_at TIMESTAMP
- **Indices:**
  - (tenant_id, created_at DESC)
  - (severity, created_at DESC)
  - (resolved_at) WHERE resolved_at IS NULL (active alerts)

## 🧪 Tests
- **File:** `src/test/java/com/leadflow/backend/service/billing/WebhookAlertServiceTest.java`
- **Coverage:** 15+ test scenarios
  - testCircuitBreakerOpenAlert()
  - testHighFailureRateAlert()
  - testProcessingStalledAlert()
  - testExcessiveRetriesAlert()
  - testTimeoutDetectionAlert()
  - testAlertDeduplication()
  - testResolveAlert()
  - testGetActiveAlerts()
  - testAlertController endpoints
  - Edge cases & boundary conditions

## 🔄 Integration Points

### CircuitBreakerConfig
- Quando transiciona para OPEN → dispara alert CIRCUIT_BREAKER_OPENED
- Método: `WebhookAlertService.checkCircuitBreakerStatus()`

### StripeEventRetryScheduler
- A cada ciclo de retry → verifica failure rate + processing latency
- A cada 5 min (scheduled) → verifica se processamento travou

### BillingDashboardService
- `getWebhookDashboard()` → incluir `activeAlerts` no response
- Dashboard mostra badges de alerts críticos

## ⏱️ Timeline
- Create WebhookAlertEvent entity: 15 min
- Create database migration V10: 10 min
- Create WebhookAlertRepository: 10 min
- Create WebhookAlertService: 30 min
- Create WebhookAlertListener: 20 min
- Create WebhookAlertController: 20 min
- Create WebhookAlertDTO: 10 min
- Create WebhookAlertServiceTest: 30 min
- Total: ~2.5 horas (145 min)

## 📝 Enums

### AlertType
```java
enum AlertType {
  CIRCUIT_BREAKER_OPENED,
  HIGH_FAILURE_RATE,
  PROCESSING_STALLED,
  EXCESSIVE_RETRIES,
  TIMEOUT_DETECTED,
  DATABASE_ERROR
}
```

### AlertSeverity
```java
enum AlertSeverity {
  CRITICAL,    // Demanda ação imediata
  WARNING,     // Situação anormal
  INFO         // Informativo
}
```

## ✅ Success Criteria
- ✅ Todas as 6 rotas de alerta funcionando
- ✅ Alerts persistidos no DB
- ✅ De-duplicação funcionando (max 1 alert do mesmo tipo/min)
- ✅ Resolução de alerts funciona
- ✅ Controllers com auth ADMIN
- ✅ 15+ testes passando
- ✅ Build SUCCESS
- ✅ Commit com mensagem descritiva
