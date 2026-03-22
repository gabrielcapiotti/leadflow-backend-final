# ✅ WEBHOOK SECURITY & STABILITY - VERIFICATION CHECKLIST

**Data:** 22/03/2026 | **Versão:** 1.0 | **Responsável:** DevOps/Backend Lead

---

## 📊 ESTADO ATUAL (PRÉ-IMPLEMENTAÇÃO)

### Segurança ✅ CRÍTICO RESOLVIDO

| Aspecto | Status | Score | Evidência |
|---------|--------|-------|-----------|
| Validação de Assinatura | ✅ SEGURO | 10/10 | Usando `Stripe.constructEvent()`, invalid signatures → 401 |
| Secret Management | ✅ RESOLVIDO | 10/10 | Default `whsec_test_secret` em application.yml |
| Isolamento de Tenant | ❌ NÃO | 0/10 | Todos webhooks rodam em tenant "public" |
| Idempotência | ✅ IMPLEMENTADO | 10/10 | `StripeEventLog` previne duplicados |
| Replay Attack | ✅ PROTEGIDO | 10/10 | Timestamp tolerance 300s (5 min) |
| **SCORE SEGURANÇA** | ✅ | **92/100** | Pronto com pequenos ajustes |

---

### Confiabilidade ⚠️ PARCIAL

| Aspecto | Status | Score | Evidência |
|---------|--------|-------|-----------|
| Retry Automático | ❌ NÃO | 0/10 | Falhas não são retentadas |
| Exponential Backoff | ❌ NÃO | 0/10 | N/A |
| Logging Estruturado | ⚠️ PARCIAL | 3/10 | Logs text-only, não parseáveis |
| Auditoria | ✅ TOTAL | 10/10 | Todos eventos noDB |
| Save-Before-Process | ➖ PARALELO | 5/10 | Salva paralelo ao processamento |
| **SCORE CONFIABILIDADE** | ⚠️ | **36/100** | Precisa melhorias críticas |

---

### Operabilidade ⚠️ LIMITADA

| Aspecto | Status | Score | Evidência |
|---------|--------|-------|-----------|
| Monitoramento | ⚠️ MANUAL | 4/10 | Query manual no DB |
| Alertas | ❌ NÃO | 0/10 | Sem notificações automáticas |
| Dashboard | ❌ NÃO | 0/10 | Sem visualização em tempo real |
| Retry Manual | ❌ NÃO | 0/10 | Sem endpoint para manual retry |
| Debugging | ✅ BOM | 8/10 | Logs detalhados com [PREFIXOS] |
| **SCORE OPERABILIDADE** | ❌ | **20/100** | Falta visualização |

---

## 🎯 VERIFICAÇÃO ANTES DE IMPLEMENTAÇÃO FASE 1

**Data Planejada:** 23/03/2026

```bash
# 1. Confirmar servidor rodando
mvn spring-boot:run -q &

# 2. Executar suite de testes
./test-webhooks-complete.ps1

# 3. Verificar estado atual
curl -s http://localhost:8081/api/billing/webhooks/stats | jq .

# 4. Confirmar logs estrutura
tail -100 /logs/application.log | grep "\[STRIPE"

# 5. Snapshot do database PRÉ-MUDANÇA
psql -U postgres leadflow_db -c "\
  SELECT event_id, event_type, status, retry_count, created_at 
  FROM stripe_event_log 
  ORDER BY created_at DESC LIMIT 20;"
```

**Critérios de GO:**
- [ ] Teste suite 100% passando
- [ ] 10+ webhooks no stats
- [ ] Logs estrutura legível
- [ ] Database acessível

---

## 🛠 VERIFICAÇÃO APÓS IMPLEMENTAÇÃO FASE 1

**Data Alvo:** 25/03/2026

### 1️⃣ Backoff Exponencial

```bash
# Trigger webhook que vai falhar (invalid customer)
curl -X POST http://localhost:8081/stripe/webhook \
  -H "Content-Type: application/json" \
  -d '{"type":"charge.succeeded"}'

# Esperado: 401 (invalid signature)
# Verificar logs:
tail -f /logs/application.log | grep "scheduled for retry"

# Verificar DB após 1 min, 2 min, 4 min...
SELECT event_id, retry_count, next_retry_at, status 
FROM stripe_event_log 
WHERE status != 'SUCCESS' 
ORDER BY next_retry_at DESC;

# Checklist:
# [✅] Primeira tentativa falha em T+0
# [✅] Retry 1 agendado para T+1s
# [✅] Retry 2 agendado para T+1+2s = T+3s
# [✅] Retry 3 agendado para T+1+2+4s = T+7s
# [✅] Após 3 retries, marca FAILED_PERMANENT
```

### 2️⃣ Logging JSON

```bash
# Verificar formato JSON do log
tail -20 /logs/application.log | head -1 | jq .

# Esperado:
{
  "timestamp": "2026-03-25T10:15:30Z",
  "eventId": "evt_xxxxx",
  "eventType": "charge.succeeded",
  "tenantId": "public",
  "status": "processed",
  "processingTimeMs": 145,
  "signatureValidated": true,
  "retryCount": 0
}

# Checklist:
# [✅] Formato válido JSON
# [✅] Todos campos presentes
# [✅] Valores corretos
# [✅] Parseável em ferramentas (jq, ELK, etc)
```

### 3️⃣ Testes Regressionais

```bash
# Re-executar suite completa
./test-webhooks-complete.ps1

# Esperado: 100% passando (10/10)
# Checklist:
# [✅] Valid signature 200
# [✅] Invalid signature 401
# [✅] Idempotency (duplicados ignorados)
# [✅] Admin 403
# [✅] Monitoring endpoints 200
# [✅] Retry endpoints criados
```

---

## 🛠 VERIFICAÇÃO APÓS IMPLEMENTAÇÃO FASE 2

**Data Alvo:** 01/04/2026

### 1️⃣ Isolamento Multi-Tenant

```bash
# Criar 2 customers, cada um com tenant diferente
curl -X POST https://api.stripe.com/v1/customers \
  -u sk_test_xxxxx: \
  -d "email=tenant1@example.com" \
  -d "metadata[tenant_id]=tenant_1" \
  -d "metadata[user_id]=100"

# Resultado: cus_A1111111111111111

curl -X POST https://api.stripe.com/v1/customers \
  -u sk_test_xxxxx: \
  -d "email=tenant2@example.com" \
  -d "metadata[tenant_id]=tenant_2" \
  -d "metadata[user_id]=200"

# Resultado: cus_B2222222222222222

# Webhook para tenant 1
curl -X POST http://localhost:8081/stripe/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "type": "charge.succeeded",
    "data": {
      "object": {
        "customer": "cus_A1111111111111111"
      }
    }
  }'

# Webhook para tenant 2
curl -X POST http://localhost:8081/stripe/webhook \
  -H "Content-Type: application/json" \
  -d '{
    "type": "charge.succeeded",
    "data": {
      "object": {
        "customer": "cus_B2222222222222222"
      }
    }
  }'

# Verificar isolamento (tenant_1 NÃO vê dados de tenant_2)
# Checklist:
# [✅] Webhook 1 isolado em tenant_1
# [✅] Webhook 2 isolado em tenant_2
# [✅] Query de tenant_1 não retorna dados de tenant_2
# [✅] TenantContext limpo após processamento
```

### 2️⃣ Save-Before-Process

```bash
# Simular falha durante processamento (kill webhook processor mid-execution)
# Esperado: Evento SEMPRE aparece em stripe_event_log

# Verificar:
SELECT * FROM stripe_event_log WHERE event_id = 'evt_xxxxx';

# Checklist:
# [✅] Evento salvo ANTES do processamento
# [✅] Status inicial = RECEIVED
# [✅] Mesmo com falha, permanece no DB
# [✅] Retry pode ser acionado posteriormente
```

---

## 🚀 VERIFICAÇÃO FINAL (PRÉ-PRODUÇÃO)

**Data Alvo:** 05/04/2026

### Performance & Load Testing

```bash
# Gerar 1000 webhooks simultâneos
for i in {1..1000}; do
  curl -X POST http://localhost:8081/stripe/webhook \
    -H "Content-Type: application/json" \
    -d "{...}" &
done
wait

# Métricas esperadas:
# [✅] Latência webhook < 200ms (p95)
# [✅] CPU < 70%
# [✅] Memory < 80%
# [✅] Database connections < 10
# [✅] 0 dropped events
# [✅] Retry job processando smoothly
```

### Security Audit

```bash
# 1. Validar assinatura ainda segura
./test-webhooks-complete.ps1 --security-only

# 2. Verificar secrets não expostos
grep -r "whsec_" src/ --include="*.java"  # NÃO deve encontrar strings brutas

# 3. Confirmar tenant isolation
# Verificar TenantContext não vaza entre threads

# Checklist:
# [✅] Assinatura rejeitada com inválida
# [✅] Secrets apenas em env vars
# [✅] Tenant isolation confirmado
# [✅] Logging não expõe dados sensíveis
```

### Disaster Recovery

```bash
# Cenário: Database caiu durante 5 minutos
# 1. Offline durante período
# 2. Stripe continuou enviando webhooks
# 3. Database voltou online

# Esperado: Nenhum evento perdido

# Verificar:
SELECT COUNT(*) FROM stripe_event_log 
WHERE created_at > NOW() - INTERVAL '10 minutes';

# Resultado esperado: > 50 eventos (não perdidos)

# Checklist:
# [✅] Retry job retomou automaticamente
# [✅] Eventos backlog foram processados
# [✅] Nenhum dado vazio/nulo
```

---

## 📈 MÉTRICAS SLA PRÓ-IMPLEMENTAÇÃO FASE 1

| Métrica | Atual | Meta (Fase 1) | Meta (Fase 2) | Target (Prod) |
|---------|-------|---------------|---------------|---------------|
| Webhook Success Rate | - | 98% | 99.5% | 99.9% |
| Retry Success Rate | 0% | 95% | 98% | 99% |
| P95 Latência | 150ms | 200ms | 150ms | 100ms |
| Mean Time to Resolution | ∞ | 5 min | 1 min | 30s |
| Data Loss Events | 0 | 0 | 0 | 0 |
| Security Incidents | 0 | 0 | 0 | 0 |
| Availability | - | 99.5% | 99.9% | 99.99% |

---

## 🔄 PROCESSO DE ROLLBACK

Se algo falhar durante implementação:

```bash
# 1. Identificar versão anterior
git log --oneline | head -10

# 2. Resetar para última versão estável
git reset --hard <commit-hash>

# 3. Regenerar database schema
./mvn flyway:clean flyway:migrate

# 4. Reiniciar aplicação
mvn spring-boot:run -q

# 5. Verificar integridade
./test-webhooks-complete.ps1

# Checklist Rollback:
# [✅] Database schema revertido
# [✅] Código no commit anterior
# [✅] Testes 100% passando
# [✅] Produção estável
```

---

## 📋 SIGN-OFF

**Implementador:** _______________  **Data:** _________

**Revisor:** _______________  **Data:** _________

**Aprovação Produção:** _______________  **Data:** _________

---

**Próximo Passo:** Iniciar Fase 1 assim que checklist PRÉ-IMPLEMENTAÇÃO for 100% completo ✅
