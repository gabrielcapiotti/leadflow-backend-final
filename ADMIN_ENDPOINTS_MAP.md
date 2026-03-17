# 📋 Mapeamento Completo de Endpoints de Admin

## Configuração Base
- **Base URL**: `http://localhost:8081/admin`
- **Context Path**: `/api` (configurado em application-dev.yml e application-prod.yml)
- **Autenticação Obrigatória**: JWT Bearer Token
- **Autorização**: Requer role `ADMIN` (anotação @PreAuthorize("hasRole('ADMIN')"))
- **Validação**: Ativada com @Validated

---

## 1️⃣ OVERVIEW - GET `/admin/overview`

### Descrição
Retorna um overview geral do sistema admin com métricas consolidadas

### Segurança
- ✅ Requer autenticação JWT
- ✅ Requer role ADMIN
- ✅ Sem validação de body

### Parâmetros
- Nenhum parâmetro obrigatório ou opcional

### Resposta Sucesso (200 OK)
```json
{
  "totalVendors": 25,
  "activeVendors": 22,
  "totalLeads": 1250,
  "activeLeads": 980,
  "totalRevenue": 12500.50,
  "mrr": 8750.75,
  "churnRate": 0.05,
  "conversionRate": 0.35,
  "avgLeadValue": 225.50,
  "totalUsers": 145,
  "activeUsers": 98
}
```

### Respostas de Erro
| Código | Situação |
|--------|----------|
| 401 | Token JWT ausente ou inválido |
| 403 | Usuário não possui role ADMIN |
| 500 | Erro ao calcular overview |

### Fluxo Interno
1. `AdminController.overview()` - Valida autenticação e autorização
2. `AdminService.getOverview()` - Calcula métricas consolidadas
3. Retorna `AdminOverviewResponse` com dados aggregados

### Validações Aplicadas
- ✅ Autenticação JWT obrigatória
- ✅ Role ADMIN obrigatório

---

## 2️⃣ GROWTH METRICS - GET `/admin/metrics/growth`

### Descrição
Retorna métricas de crescimento do sistema para um período específico

### Segurança
- ✅ Requer autenticação JWT
- ✅ Requer role ADMIN

### Parâmetros
```
GET /admin/metrics/growth?days=30
```

| Parâmetro | Tipo | Default | Validação | Descrição |
|-----------|------|---------|-----------|-----------|
| `days` | Integer | 30 | @Min(1), @Max(365) | Número de dias para análise de crescimento |

### Resposta Sucesso (200 OK)
```json
{
  "period": "2026-02-16 a 2026-03-17",
  "days": 30,
  "vendorGrowth": 0.15,
  "leadGrowth": 0.28,
  "revenueGrowth": 0.42,
  "userGrowth": 0.10,
  "mrrGrowth": 0.35,
  "dailyGrowth": [
    {
      "date": "2026-02-16",
      "vendors": 23,
      "leads": 890,
      "revenue": 8950.00,
      "users": 85
    },
    ...
  ]
}
```

### Respostas de Erro
| Código | Situação |
|--------|----------|
| 400 | days < 1 ou days > 365 |
| 401 | Token JWT ausente ou inválido |
| 403 | Usuário não possui role ADMIN |
| 500 | Erro ao calcular métricas |

### Fluxo Interno
1. `AdminController.growth()` - Valida parâmetro days (1-365)
2. `AdminService.getGrowth(days)` - Calcula crescimento para período
3. Retorna `GrowthResponse` com dados históricos e percentuais

### Validações Aplicadas
- ✅ Autenticação JWT obrigatória
- ✅ Role ADMIN obrigatório
- ✅ days >= 1
- ✅ days <= 365

---

## 3️⃣ COHORT ANALYSIS - GET `/admin/metrics/cohorts`

### Descrição
Retorna análise de coortes (grupos de usuários/leads por período)

### Segurança
- ✅ Requer autenticação JWT
- ✅ Requer role ADMIN
- ✅ Sem parâmetros

### Parâmetros
- Nenhum parâmetro obrigatório ou opcional

### Resposta Sucesso (200 OK)
```json
[
  {
    "cohortId": "2025-Q1",
    "createdAt": "2025-01-01",
    "vendorsInCohort": 8,
    "leadsInCohort": 320,
    "survivalRate": 0.82,
    "mrr": 4500.00,
    "churnRate": 0.05,
    "averageLifetime": 180
  },
  {
    "cohortId": "2025-Q2",
    "createdAt": "2025-04-01",
    "vendorsInCohort": 12,
    "leadsInCohort": 450,
    "survivalRate": 0.88,
    "mrr": 6200.00,
    "churnRate": 0.03,
    "averageLifetime": 140
  },
  ...
]
```

### Respostas de Erro
| Código | Situação |
|--------|----------|
| 401 | Token JWT ausente ou inválido |
| 403 | Usuário não possui role ADMIN |
| 500 | Erro ao calcular coortes |

### Fluxo Interno
1. `AdminController.cohorts()` - Valida autenticação e autorização
2. `AdminService.calculateCohorts()` - Agrupa dados por período
3. Retorna `List<CohortResponse>` com análise de coortes

### Validações Aplicadas
- ✅ Autenticação JWT obrigatória
- ✅ Role ADMIN obrigatório

---

## 4️⃣ MRR FORECAST - GET `/admin/metrics/forecast`

### Descrição
Retorna previsão de MRR (Monthly Recurring Revenue) para meses futuros

### Segurança
- ✅ Requer autenticação JWT
- ✅ Requer role ADMIN

### Parâmetros
```
GET /admin/metrics/forecast?months=6
```

| Parâmetro | Tipo | Default | Validação | Descrição |
|-----------|------|---------|-----------|-----------|
| `months` | Integer | 6 | @Min(1), @Max(24) | Número de meses para previsão |

### Resposta Sucesso (200 OK)
```json
[
  {
    "monthNumber": 1,
    "month": "2026-04",
    "forecastedMrr": 9250.00,
    "confidence": 0.95,
    "lowerBound": 8800.00,
    "upperBound": 9700.00,
    "trend": "upward",
    "expectedNewVendors": 3,
    "expectedChurnRate": 0.04
  },
  {
    "monthNumber": 2,
    "month": "2026-05",
    "forecastedMrr": 9800.00,
    "confidence": 0.92,
    "lowerBound": 9200.00,
    "upperBound": 10400.00,
    "trend": "upward",
    "expectedNewVendors": 2,
    "expectedChurnRate": 0.04
  },
  ...
]
```

### Respostas de Erro
| Código | Situação |
|--------|----------|
| 400 | months < 1 ou months > 24 |
| 401 | Token JWT ausente ou inválido |
| 403 | Usuário não possui role ADMIN |
| 500 | Erro ao calcular forecast |

### Fluxo Interno
1. `AdminController.forecast()` - Valida parâmetro months (1-24)
2. `AdminService.forecastMRR(months)` - Calcula previsão com algoritmo de regressão
3. Retorna `List<ForecastPoint>` com previsões por mês

### Validações Aplicadas
- ✅ Autenticação JWT obrigatória
- ✅ Role ADMIN obrigatório
- ✅ months >= 1
- ✅ months <= 24

---

## 5️⃣ VENDOR HEALTH - GET `/admin/metrics/health/{vendorId}`

### Descrição
Retorna saúde/status detalhado de um vendor específico

### Segurança
- ✅ Requer autenticação JWT
- ✅ Requer role ADMIN

### Parâmetros
```
GET /admin/metrics/health/550e8400-e29b-41d4-a716-446655440000
```

| Parâmetro | Tipo | Validação | Descrição |
|-----------|------|-----------|-----------|
| `vendorId` | UUID | @NotNull | UUID do vendor |

### Resposta Sucesso (200 OK)
```json
{
  "vendorId": "550e8400-e29b-41d4-a716-446655440000",
  "vendorName": "Tech Leads Inc",
  "status": "HEALTHY",
  "healthScore": 0.92,
  "totalLeads": 145,
  "activeLeads": 128,
  "conversionRate": 0.42,
  "monthlyRevenue": 2850.00,
  "mrrTrend": 0.15,
  "lastActivityAt": "2026-03-17T10:30:00Z",
  "leadsThisMonth": 45,
  "conversionThisMonth": 0.38,
  "outstandingBalance": 0.00,
  "subscriptionStatus": "ACTIVE",
  "riskFactors": []
}
```

### Respostas de Erro
| Código | Situação |
|--------|----------|
| 400 | vendorId não é UUID válido |
| 401 | Token JWT ausente ou inválido |
| 403 | Usuário não possui role ADMIN |
| 404 | Vendor não encontrado |
| 500 | Erro ao calcular health |

### Fluxo Interno
1. `AdminController.health()` - Valida vendorId como UUID não-nulo
2. `AdminService.calculateHealth(vendorId)` - Calcula score de saúde
3. Analisa engagement, conversão e riscos
4. Retorna `VendorHealthResponse` com status completo

### Validações Aplicadas
- ✅ Autenticação JWT obrigatória
- ✅ Role ADMIN obrigatório
- ✅ vendorId não-nulo
- ✅ vendorId formato UUID

---

## 📊 Resumo dos Endpoints

| Endpoint | Method | Autenticação | Autorização | Status |
|----------|--------|--------------|-------------|--------|
| `/admin/overview` | GET | JWT | ADMIN | ✅ Implementado |
| `/admin/metrics/growth` | GET | JWT | ADMIN | ✅ Implementado |
| `/admin/metrics/cohorts` | GET | JWT | ADMIN | ✅ Implementado |
| `/admin/metrics/forecast` | GET | JWT | ADMIN | ✅ Implementado |
| `/admin/metrics/health/{vendorId}` | GET | JWT | ADMIN | ✅ Implementado |

---

## 🔐 Requisitos de Segurança Globais

- **Autenticação**: Todos os endpoints requerem JWT Bearer Token válido
- **Autorização**: Todos os endpoints requerem role ADMIN
- **Rate Limiting**: Configurado no application-dev.yml e application-prod.yml
- **Validação**: @Validated ativado no controller
- **CORS**: Configurado em SecurityConfig
- **HTTPS**: Recomendado em produção

---

## 🧪 Teste Rápido

### 1. Obter Token Admin
```bash
curl -X POST http://localhost:8081/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "email": "admin@leadflow.com",
    "password": "AdminPassword@123"
  }'
```

### 2. Testar Overview
```bash
curl -X GET http://localhost:8081/admin/overview \
  -H "Authorization: Bearer {ACCESS_TOKEN}"
```

### 3. Testar Growth Metrics (30 dias)
```bash
curl -X GET "http://localhost:8081/admin/metrics/growth?days=30" \
  -H "Authorization: Bearer {ACCESS_TOKEN}"
```

### 4. Testar Cohorts
```bash
curl -X GET http://localhost:8081/admin/metrics/cohorts \
  -H "Authorization: Bearer {ACCESS_TOKEN}"
```

### 5. Testar Forecast (6 meses)
```bash
curl -X GET "http://localhost:8081/admin/metrics/forecast?months=6" \
  -H "Authorization: Bearer {ACCESS_TOKEN}"
```

### 6. Testar Vendor Health
```bash
curl -X GET "http://localhost:8081/admin/metrics/health/550e8400-e29b-41d4-a716-446655440000" \
  -H "Authorization: Bearer {ACCESS_TOKEN}"
```

---

## 📝 Notas Importantes

1. **Rate Limiting**: Os endpoints estão sujeitos ao rate limiting configurado
2. **Timezone**: Todas as datas são retornadas em UTC
3. **Paginação**: Alguns endpoints podem suportar paginação (verificar implementação)
4. **Cache**: Algumas respostas podem ser cacheadas para performance
5. **Monitoramento**: Todos os acessos são logados para auditoria

---

**Última Atualização**: March 17, 2026
**Versão da API**: 1.0
**Status**: Todos os endpoints operacionais ✅
