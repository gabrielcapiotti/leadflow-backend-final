# Phase 3 ETAPA 4: Observability & Metrics (Micrometer)

**Status**: 🚀 STARTING  
**Target**: Comprehensive webhook system observability via Micrometer + Prometheus metrics  
**Duration**: ~2.5 hours  
**Complexity**: HIGH (distributed metrics collection & aggregation)

---

## 📊 Overview

**ETAPA 4** implements production-grade observability for the webhook system using:
- **Micrometer**: Spring Boot 3.x metrics abstraction
- **Prometheus**: Time-series metrics storage (via Spring Cloud native)
- **Custom Meters**: Webhook-specific business metrics
- **Tags/Labels**: Multi-tenant & dimensional data
- **Real-time Dashboards**: Prometheus → Grafana ready

### Key Metrics to Track

#### 1. **Throughput Metrics**
- `webhook.events.received` (Counter) - Total events received
- `webhook.events.processed` (Counter) - Successfully processed
- `webhook.events.failed` (Counter) - Failed events
- `webhook.event.processing.time` (Timer) - Duration per event

#### 2. **Failure Metrics**
- `webhook.failures.total` (Counter) - Categorized by reason (timeout, database, validation)
- `webhook.retry.attempts` (Counter) - Total retry attempts
- `webhook.retry.success.rate` (Gauge) - % of succeeding retries
- `webhook.event.retry.count` (Distribution) - Histogram of retry counts

#### 3. **Circuit Breaker Metrics**
- `webhook.circuit.breaker.state` (Gauge) - Current state (0=CLOSED, 1=OPEN, 2=HALF_OPEN)
- `webhook.circuit.breaker.transitions` (Counter) - State changes
- `webhook.circuit.breaker.rejected.requests` (Counter) - Rejected due to OPEN state

#### 4. **Processing Metrics**
- `webhook.processing.queue.size` (Gauge) - Pending events
- `webhook.latency.p50/p95/p99` (Timer tags) - Percentile latencies
- `webhook.event.age` (Gauge) - Time since event created (aging detection)

#### 5. **Database Metrics**
- `webhook.database.errors` (Counter) - Database operation failures
- `webhook.database.connection.pool` (Gauge) - Active connections
- `webhook.event.saves` (Counter) - Records saved to DB

#### 6. **Alert Metrics**
- `webhook.alerts.created` (Counter) - Alerts triggered
- `webhook.alerts.critical.count` (Gauge) - Active CRITICAL alerts
- `webhook.alerts.resolved` (Counter) - Resolved count

#### 7. **Tenant Metrics** (Multi-dimensional)
- All above metrics tagged with `tenant_id`
- `webhook.tenant.event.count` (Gauge per tenant)
- `webhook.tenant.failure.rate` (Gauge per tenant)

---

## 🏗️ Architecture

```
┌─────────────────────────────────────────────────────────────────┐
│                     Micrometer Registry                         │
│  (Spring Boot auto-configures SimpleMeterRegistry or Prometheus)│
└────────────────┬────────────────────────────────────────────────┘
                 │
     ┌───────────┼───────────┐
     │           │           │
  Meters      Tags      Observations
     │           │           │
     ├─Counter   ├─tenant_id ├─Count events
     ├─Gauge     ├─status    ├─Queue depth
     ├─Timer     ├─reason    ├─Processing time
     ├─Histogram ├─event_type├─Message count
     └─Summary   └─app       └─P50/P95/P99
     
     ↓
     
┌──────────────────────────────┐
│  Prometheus Scrape Endpoint  │
│  (Auto-exposed at /actuator  │
│   /prometheus via mvcEndpoint)
└──────────────────────────────┘
     │
     ↓
┌──────────────────────────────┐
│   Prometheus Time-Series DB  │
└──────────────────────────────┘
     │
     ↓
┌──────────────────────────────┐
│  Grafana Dashboard           │
│  (External - integrates)     │
└──────────────────────────────┘
```

---

## 📁 Files to Create

### 1. **WebhookMetricsConfig.java** (NEW)
**Location**: `src/main/java/com/leadflow/backend/config/WebhookMetricsConfig.java`  
**Purpose**: Micrometer registry setup + custom meter initialization  
**Size**: ~200 lines

```java
@Configuration
public class WebhookMetricsConfig {
    
    // Initialize MeterRegistry from Spring auto-config
    @Bean
    public WebhookMetricsTracker webhookMetricsTracker(MeterRegistry registry) {
        return new WebhookMetricsTracker(registry);
    }
    
    // Optional: Custom Prometheus endpoint configuration
    @Bean
    public MeterRegistryCustomizer<MeterRegistry> prometheusConfig() {
        return registry -> {
            // Add global tags
            registry.config().commonTags(
                "app", "leadflow-backend",
                "service", "webhook-processor",
                "environment", activeProfile
            );
        };
    }
}
```

### 2. **WebhookMetricsTracker.java** (NEW)
**Location**: `src/main/java/com/leadflow/backend/service/billing/WebhookMetricsTracker.java`  
**Purpose**: Centralized metrics recording for all webhook operations  
**Size**: ~400 lines

Key Methods:
- `recordEventReceived(UUID tenantId, String eventType)` → Counter ++
- `recordEventProcessed(UUID tenantId, long durationMs)` → Timer record
- `recordEventFailed(UUID tenantId, String reason)` → Counter ++
- `recordRetryAttempt(UUID tenantId, int retryCount)` → Counter, Gauge
- `recordCircuitBreakerStateChange(CircuitState oldState, CircuitState newState)` → Counter
- `recordQueueSize(int size)` → Gauge update
- `recordAlertCreated(AlertType type, AlertSeverity severity)` → Counter
- `recordDatabaseError(String operation, String reason)` → Counter
- `getMetricsSummary()` → Returns current metrics snapshot

### 3. **WebhookEventMetrics.java** (NEW)
**Location**: `src/main/java/com/leadflow/backend/dto/metrics/WebhookEventMetrics.java`  
**Purpose**: DTO for metrics API responses  
**Size**: ~200 lines

```java
@Data
@Builder
public class WebhookEventMetrics {
    // Throughput
    long totalReceived;
    long totalProcessed;
    long totalFailed;
    double successRate;
    
    // Latency
    double avgProcessingTimeMs;
    double p50LatencyMs;
    double p95LatencyMs;
    double p99LatencyMs;
    
    // Failures
    Map<String, Long> failuresByReason;  // timeout, database, validation
    
    // Retries
    long totalRetries;
    double retrySuccessRate;
    int maxRetryCount;
    
    // Circuit Breaker
    CircuitState cbState;
    long cbTransitions;
    long cbRejectedRequests;
    
    // Queue
    int queueSize;
    int maxQueueSize;
}
```

### 4. **WebhookMetricsController.java** (NEW)
**Location**: `src/main/java/com/leadflow/backend/controller/billing/WebhookMetricsController.java`  
**Purpose**: REST endpoints for metrics queries + real-time dashboard  
**Size**: ~300 lines

**Endpoints**:
- `GET /api/v1/billing/webhooks/metrics` → System-wide metrics summary
- `GET /api/v1/billing/webhooks/metrics/tenant/{tenantId}` → Tenant-specific metrics
- `GET /api/v1/billing/webhooks/metrics/real-time` → Stream live metrics (SSE)
- `GET /api/v1/billing/webhooks/metrics/failures/breakdown` → Failure reason distribution
- `GET /api/v1/billing/webhooks/metrics/latency/percentiles` → Per-event latencies
- `GET /actuator/prometheus` → Native Prometheus endpoint (auto)

### 5. **StripeEventRetryScheduler.java** (MODIFY) ✏️
**Change**: Integrate metric recording at key points  
**Lines to add**: ~30 lines
```java
- Record queue size when scheduler runs
- Record CB state changes
- Record retry success/failure
- Record latency on each processed event
```

### 6. **StripeWebhookController.java** (MODIFY) ✏️
**Change**: Record incoming events and processing results  
**Lines to add**: ~25 lines
```java
- Record event received (count + tags)
- Record processing result (success/failed)
- Record failure reason
```

### 7. **WebhookAlertListener.java** (MODIFY) ✏️
**Change**: Record alerts as metrics  
**Lines to add**: ~15 lines
```java
- Record alert created
- Record alert severity distribution
- Record alert type distribution
```

### 8. **WebhookMetricsControllerTest.java** (NEW)
**Location**: `src/test/java/com/leadflow/.../WebhookMetricsControllerTest.java`  
**Purpose**: Test metrics endpoints  
**Size**: ~300 lines
**Scenarios**:
- Metrics correctly aggregated
- Tenant isolation in metrics
- Percentile calculations accurate
- Failure breakdown correctly categorized
- Circuit breaker state reflected
- Real-time stream endpoints work

---

## 🔧 Implementation Steps

### STEP 1: Create WebhookMetricsConfig (5 min)
- Spring auto-detects Micrometer on classpath
- Configure registry + common tags
- Inject into WebhookMetricsTracker

### STEP 2: Create WebhookMetricsTracker (30 min)
- Implement all 8 recording methods
- Use MeterRegistry API:
  - `Counter.increment()`
  - `Timer.record()`
  - `Gauge.gauge()`
  - `DistributionSummary.record()`
- Tag all metrics with tenant_id, status, reason
- Thread-safe (Micrometer handles this)

### STEP 3: Create WebhookEventMetrics DTO (10 min)
- Simple POJO with @Data/@Builder
- Match metric names from tracker

### STEP 4: Modify StripeWebhookController (15 min)
- Inject WebhookMetricsTracker
- Record on event received
- Record on success/failure
- Pass tenant_id + event type

### STEP 5: Modify StripeEventRetryScheduler (15 min)
- Record queue size (Gauge)
- Record CB state changes (Counter)
- Record event processed (Timer)
- Record retry success/failure

### STEP 6: Modify WebhookAlertListener (10 min)
- Record alerts created
- Track alert severity/type distribution

### STEP 7: Create WebhookMetricsController (20 min)
- Implement 6 endpoints
- Aggregate metrics from tracker
- Calculate percentiles
- Support tenant filtering
- Add @PreAuthorize("ADMIN")

### STEP 8: Create WebhookMetricsControllerTest (30 min)
- Mock MeterRegistry
- Test aggregations
- Verify tags/dimensions
- Test failure breakdown
- Test percentile calculations

### STEP 9: Modify pom.xml (if needed - 5 min)
**Verify dependencies exist**:
- `org.springframework.boot:spring-boot-starter-actuator`
- `io.micrometer:micrometer-core` (auto-included)
- `io.micrometer:micrometer-registry-prometheus` (optional, for native export)

### STEP 10: Compile & Verify (10 min)
- `mvn clean compile`
- Verify no errors
- `mvn clean test` (run new test suite)

### STEP 11: Commit (5 min)
```
git commit -m "feat: Phase 3 Etapa 4 - Webhook Observability & Metrics

- Create WebhookMetricsConfig for Micrometer setup
- Create WebhookMetricsTracker with 8 core recording methods
- Integrate metrics into StripeWebhookController
- Integrate metrics into StripeEventRetryScheduler  
- Record alert metrics in WebhookAlertListener
- Create WebhookMetricsController with 6 endpoints:
  * GET /api/v1/billing/webhooks/metrics - system summary
  * GET /api/v1/billing/webhooks/metrics/tenant/{id} - tenant slice
  * GET /api/v1/billing/webhooks/metrics/real-time - SSE stream
  * GET /api/v1/billing/webhooks/metrics/failures/breakdown - reason distribution
  * GET /api/v1/billing/webhooks/metrics/latency/percentiles - latency analysis
  * GET /actuator/prometheus - native endpoint

- Create WebhookMetricsControllerTest with 15+ scenarios
- Verify Prometheus metrics export working
- All tests passing ✅"
```

---

## 📊 Micrometer API Cheat Sheet

```java
// COUNTER - Monotonically increasing
Counter.builder("webhook.events.received")
    .tag("tenant_id", tenantId.toString())
    .tag("event_type", eventType)
    .register(meterRegistry)
    .increment();

// TIMER - Records duration
Timer.builder("webhook.processing.time")
    .publishPercentiles(0.5, 0.95, 0.99)
    .tag("tenant_id", tenantId.toString())
    .register(meterRegistry)
    .record(durationMs, TimeUnit.MILLISECONDS);

// GAUGE - Current value (snapshot)
AtomicInteger queueSize = new AtomicInteger(0);
Gauge.builder("webhook.queue.size", queueSize::get)
    .register(meterRegistry);

// DISTRIBUTION SUMMARY - Percentiles on arbitrary values
DistributionSummary.builder("webhook.retry.count")
    .scale(1.0)
    .tag("tenant_id", tenantId.toString())
    .register(meterRegistry)
    .record(retryCount);
```

---

## 🎯 Success Criteria

✅ **ETAPA 4 Complete When**:
1. All 7 metric types (counter, gauge, timer, etc.) implemented
2. WebhookMetricsTracker fully functional with 8+ methods
3. All 6 controller endpoints working + ADMIN auth
4. Metrics integrated into 3 services (Controller, Scheduler, Listener)
5. 15+ test scenarios passing ✅
6. Prometheus endpoint `/actuator/prometheus` returning metrics
7. Metrics correctly tagged with tenant_id for multi-tenant isolation
8. Maven compile + test SUCCESS ✅
9. Git commit success

---

## 📈 Metrics Flow Diagram

```
Event Arrives
    ↓
StripeWebhookController
    │ metrics.recordEventReceived(tenantId, type)
    ↓
StripeEventLogRepository (save)
    │ metrics.recordEventSaved()
    ↓
Processing Queue
    │ scheduler tracks queueSize (Gauge)
    ↓
StripeEventRetryScheduler (process)
    │ Timer measure: process()
    ↓
Success? ─ YES → metrics.recordEventProcessed()
         │
         NO  → metrics.recordEventFailed(reason)
              → retry logic
              → metrics.recordRetryAttempt()
              ↓
CircuitBreaker State Change
    │ metrics.recordCBTransition()
    ↓
All metrics tagged with:
    - tenant_id
    - status (success/failure)
    - reason (if failed)
    - event_type
    ↓
MeterRegistry (in-memory)
    ↓
/actuator/prometheus endpoint
    ↓
Prometheus scrapes (every 15s)
    ↓
Time-series DB
    ↓
Grafana Dashboard (external)
```

---

## 🔗 Complementary Documentation
- **Micrometer Docs**: https://micrometer.io/docs/concepts
- **Spring Boot Actuator**: https://spring.io/guides/gs/actuator-service/
- **Prometheus Integration**: https://prometheus.io/docs/instrumenting/exporters/

---

## 📌 Notes

1. **Thread Safety**: Micrometer handles all thread safety via atomic operations
2. **Performance**: Metrics recording is negligible (~microseconds per operation)
3. **GC Impact**: Minimal - most metrics stored as primitives
4. **Dashboards**: Metrics are ready for Grafana immediately after implementation
5. **Alerting**: Prometheus rules can trigger on metric thresholds (external setup)
6. **Cardinality**: Be careful with unlimited tags (tenant_id is bounded by number of tenants)

---

**Status**: Ready to proceed with ETAPA 4 implementation 🚀

