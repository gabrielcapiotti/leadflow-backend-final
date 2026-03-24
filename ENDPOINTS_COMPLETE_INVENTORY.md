# Inventário Completo de Endpoints REST do LeadFlow Backend

**Data da Análise**: 22 de Março de 2026  
**Total de Controllers**: 28  
**Total de Endpoints**: 126

---

## Sumário por Controller

| # | Controller | Total Endpoints | Endpoints |
|---|---|---|---|
| 1 | WebhookReplayController | 6 | GET /failed, GET /failed/permanent, GET /failed/recent, POST /{webhookId}/replay, GET /stats, DELETE /{webhookId} |
| 2 | SendGridWebhookController | 1 | POST |
| 3 | CaktoWebhookController | 1 | POST |
| 4 | VendorLeadController | 13 | POST /leads, GET /{id}, GET, DELETE /{id}, PUT /{id}/stage, PUT /{id}/owner, GET /metrics, GET /metrics/stage-time, GET /metrics/conversion, GET /ranking, GET /{id}/conversation, GET /{id}/alerts, PUT /{id}/resumo |
| 5 | VendorController | 4 | GET, POST, PUT /{id}, DELETE /{id} |
| 6 | UserController | 4 | GET, GET /{id}, PUT /{id}, DELETE /{id} |
| 7 | UsageController | 2 | GET, GET /limits |
| 8 | StripeWebhookController | 1 | POST /webhook |
| 9 | SettingController | 6 | GET, GET /{id}, PUT, DELETE, PATCH, POST /reset |
| 10 | PublicSettingController | 1 | GET /{id} |
| 11 | AdminSettingController | 3 | GET /{id}, PUT /{id}, DELETE /{id} |
| 12 | RoleController | 2 | GET, GET /{id} |
| 13 | PaymentController | 1 | POST /webhook |
| 14 | WebhookMetricsController | 4 | GET, GET /real-time, GET /failures/breakdown, GET /latency/percentiles |
| 15 | WebhookFailedEventController | 1 | POST /{webhookId}/replay |
| 16 | WebhookAlertController | 9 | GET, GET /tenant/{tenantId}, GET /critical, GET /history, GET /stats, GET /by-type/{alertType}, GET /by-severity/{severity}, POST /{alertId}/resolve, POST /resolve-by-type/{alertType} |
| 17 | FailureAnalysisController | 8 | GET /failures, GET /failures/7d, GET /failures/30d, GET /failures/window, GET /trends, GET /recommendations, GET /health, GET /breakdown |
| 18 | BillingDashboardController | 13 | GET /dashboard/{tenantId}, GET /subscription/{tenantId}, GET /events/{tenantId}, GET /usage/{tenantId}, GET /health, GET /subscription, GET /usage, POST /cancel, GET /webhooks/dashboard, GET /webhooks/recent, GET /webhooks/failures, GET /webhooks/breakdown/by-tenant, GET /webhooks/breakdown/by-type, GET /webhooks/breakdown/by-status |
| 19 | AiController | 7 | POST /chat, POST /lead-summary, POST /title-suggestion, POST /refine-message, POST /sentiment-analysis, POST /classify-lead, POST /generate-response |
| 20 | DashboardController | 1 | GET /dashboard |
| 21 | FileController | 1 | POST /upload |
| 22 | LeadStatusHistoryController | 2 | GET /{leadId}/history, GET /history/{historyId} |
| 23 | LeadController | 5 | POST, GET, GET /{id}, PATCH /{id}/status, DELETE /{id} |
| 24 | AuthController | 11 | POST /register, POST /login, GET /me, GET /sessions, DELETE /sessions/{sessionId}, DELETE /sessions, POST /refresh, POST /logout, POST /change-password, POST /forgot-password, POST /reset-password, GET /debug |
| 25 | AdminController | 5 | GET /overview, GET /metrics/growth, GET /metrics/cohorts, GET /metrics/forecast, GET /metrics/health/{vendorId} |
| 26 | AdminAuditController | 2 | GET /security, GET /vendor |
| 27 | BillingAdminController | 4 | GET /webhook-events, GET /webhook-events/{eventId}, PUT /webhook-events/{eventId}/retry, GET /webhook-stats |
| 28 | BillingController | 8 | POST /checkout, POST /webhook, GET /subscription, GET /invoices, GET /invoices/{invoiceId}, GET /payment-methods, POST /payment-methods, DELETE /payment-methods/{paymentMethodId} |

---

## Detalhamento por Controller

### 1. WebhookReplayController
**Base Path**: `/api/billing/webhooks`  
**Total Endpoints**: 6

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/failed` | Obter webhooks pendentes para retry |
| GET | `/failed/permanent` | Obter webhooks permanentemente falhados |
| GET | `/failed/recent` | Obter falhas recentes (últimas 24 horas) |
| POST | `/{webhookId}/replay` | Reprocessar manualmente um webhook |
| GET | `/stats` | Obter estatísticas de retry |
| DELETE | `/{webhookId}` | Deletar um webhook do fila de retry |

---

### 2. SendGridWebhookController
**Base Path**: `/webhooks/sendgrid`  
**Total Endpoints**: 1

| Método HTTP | Caminho | Descrição |
|---|---|---|
| POST | `/` | Processar webhook do SendGrid |

---

### 3. CaktoWebhookController
**Base Path**: `/webhooks/cakto`  
**Total Endpoints**: 1

| Método HTTP | Caminho | Descrição |
|---|---|---|
| POST | `/` | Processar webhook do Cakto |

---

### 4. VendorLeadController
**Base Path**: `/vendor-leads`, `/api/vendor-leads`  
**Total Endpoints**: 13

| Método HTTP | Caminho | Descrição |
|---|---|---|
| POST | `/leads` | Criar um novo lead de vendor |
| GET | `/{id}` | Obter lead por ID |
| GET | `/` | Listar todos os leads (paginado) |
| DELETE | `/{id}` | Deletar um lead |
| PUT | `/{id}/stage` | Atualizar stage do lead |
| PUT | `/{id}/owner` | Atribuir dono ao lead |
| GET | `/metrics` | Obter métricas de leads |
| GET | `/metrics/stage-time` | Obter tempo médio por stage |
| GET | `/metrics/conversion` | Obter taxas de conversão |
| GET | `/ranking` | Obter ranking de leads |
| GET | `/{id}/conversation` | Obter conversas do lead |
| GET | `/{id}/alerts` | Obter alertas abertos do lead |
| PUT | `/{id}/resumo` | Gerar resumo do lead |

---

### 5. VendorController
**Base Path**: `/vendors`, `/api/vendors`  
**Total Endpoints**: 4

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/` | Filtrar vendors (por email ou slug) |
| POST | `/` | Criar novo vendor |
| PUT | `/{id}` | Atualizar vendor |
| DELETE | `/{id}` | Deletar vendor |

---

### 6. UserController
**Base Path**: `/users`  
**Total Endpoints**: 4

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/` | Listar usuários ativos (paginado) |
| GET | `/{id}` | Obter usuário por ID |
| PUT | `/{id}` | Atualizar usuário |
| DELETE | `/{id}` | Deletar usuário (soft delete) |

---

### 7. UsageController
**Base Path**: `/usage`  
**Total Endpoints**: 2

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/` | Obter uso do vendor |
| GET | `/limits` | Obter limites de uso |

---

### 8. StripeWebhookController
**Base Path**: `/stripe`  
**Total Endpoints**: 1

| Método HTTP | Caminho | Descrição |
|---|---|---|
| POST | `/webhook` | Processar webhook do Stripe |

---

### 9. SettingController
**Base Path**: `/api/me/settings`  
**Total Endpoints**: 6

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/` | Obter configurações do usuário |
| GET | `/{id}` | Obter configurações por ID |
| PUT | `/` | Salvar ou atualizar configurações |
| DELETE | `/` | Deletar configurações (soft delete) |
| PATCH | `/` | Atualização parcial de configurações |
| POST | `/reset` | Resetar configurações para padrão |

---

### 10. PublicSettingController
**Base Path**: `/public/settings`  
**Total Endpoints**: 1

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/{id}` | Obter configurações públicas (sem autenticação) |

---

### 11. AdminSettingController
**Base Path**: `/api/settings`  
**Total Endpoints**: 3

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/{id}` | Obter configurações por ID (admin) |
| PUT | `/{id}` | Atualizar configurações por ID (admin) |
| DELETE | `/{id}` | Deletar configurações por ID (admin) |

---

### 12. RoleController
**Base Path**: `/api/roles`  
**Total Endpoints**: 2

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/` | Listar todas as roles |
| GET | `/{id}` | Obter role por ID |

---

### 13. PaymentController
**Base Path**: `/payments`  
**Total Endpoints**: 1

| Método HTTP | Caminho | Descrição |
|---|---|---|
| POST | `/webhook` | Processar webhook de pagamento |

---

### 14. WebhookMetricsController
**Base Path**: `/api/v1/billing/webhooks/metrics`  
**Total Endpoints**: 4

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/` | Obter métricas do sistema |
| GET | `/real-time` | Obter métricas em tempo real |
| GET | `/failures/breakdown` | Obter breakdown de falhas |
| GET | `/latency/percentiles` | Obter percentis de latência |

---

### 15. WebhookFailedEventController
**Base Path**: `/api/v1/billing/webhooks/failed`  
**Total Endpoints**: 1

| Método HTTP | Caminho | Descrição |
|---|---|---|
| POST | `/{webhookId}/replay` | Reprocessar webhook falhado |

---

### 16. WebhookAlertController
**Base Path**: `/api/v1/billing/webhooks/alerts`  
**Total Endpoints**: 9

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/` | Obter todos os alertas ativos |
| GET | `/tenant/{tenantId}` | Obter alertas de um tenant |
| GET | `/critical` | Obter alertas críticos |
| GET | `/history` | Obter histórico de alertas |
| GET | `/stats` | Obter estatísticas de alertas |
| GET | `/by-type/{alertType}` | Obter alertas por tipo |
| GET | `/by-severity/{severity}` | Obter alertas por severidade |
| POST | `/{alertId}/resolve` | Resolver um alerta |
| POST | `/resolve-by-type/{alertType}` | Resolver alertas por tipo |

---

### 17. FailureAnalysisController
**Base Path**: `/api/v1/billing/webhooks/analysis`  
**Total Endpoints**: 8

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/failures` | Análise de falhas (24 horas) |
| GET | `/failures/7d` | Análise de falhas (7 dias) |
| GET | `/failures/30d` | Análise de falhas (30 dias) |
| GET | `/failures/window` | Análise de falhas (janela customizada) |
| GET | `/trends` | Obter tendências de falhas |
| GET | `/recommendations` | Obter recomendações |
| GET | `/health` | Obter status de saúde |
| GET | `/breakdown` | Obter breakdown de falhas |

---

### 18. BillingDashboardController
**Base Path**: `/api/v1/billing`  
**Total Endpoints**: 13

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/dashboard/{tenantId}` | Dashboard de billing para tenant |
| GET | `/subscription/{tenantId}` | Detalhes de subscription |
| GET | `/events/{tenantId}` | Histórico de eventos |
| GET | `/usage/{tenantId}` | Estatísticas de uso |
| GET | `/health` | Health check do sistema |
| GET | `/subscription` | Obter subscription do vendor atual |
| GET | `/usage` | Obter uso do vendor atual |
| POST | `/cancel` | Cancelar subscription |
| GET | `/webhooks/dashboard` | Dashboard de webhooks |
| GET | `/webhooks/recent` | Webhooks recentes |
| GET | `/webhooks/failures` | Falhas de webhooks |
| GET | `/webhooks/breakdown/by-tenant` | Breakdown por tenant |
| GET | `/webhooks/breakdown/by-type` | Breakdown por tipo |
| GET | `/webhooks/breakdown/by-status` | Breakdown por status |

---

### 19. AiController
**Base Path**: `/ai`  
**Total Endpoints**: 7

| Método HTTP | Caminho | Descrição |
|---|---|---|
| POST | `/chat` | Chat com IA sobre um lead |
| POST | `/lead-summary` | Gerar resumo automático |
| POST | `/title-suggestion` | Sugerir título do lead |
| POST | `/refine-message` | Refinar mensagem |
| POST | `/sentiment-analysis` | Analisar sentimento |
| POST | `/classify-lead` | Classificar o lead |
| POST | `/generate-response` | Gerar resposta automática |

---

### 20. DashboardController
**Base Path**: Nenhum (raiz)  
**Total Endpoints**: 1

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/dashboard` | Obter dashboard do vendor |

---

### 21. FileController
**Base Path**: `/files`  
**Total Endpoints**: 1

| Método HTTP | Caminho | Descrição |
|---|---|---|
| POST | `/upload` | Upload de arquivo |

---

### 22. LeadStatusHistoryController
**Base Path**: `/leads`  
**Total Endpoints**: 2

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/{leadId}/history` | Obter histórico de status |
| GET | `/history/{historyId}` | Obter registro específico de histórico |

---

### 23. LeadController
**Base Path**: `/leads`, `/api/leads`  
**Total Endpoints**: 5

| Método HTTP | Caminho | Descrição |
|---|---|---|
| POST | `/` | Criar novo lead |
| GET | `/` | Listar leads ativos |
| GET | `/{id}` | Obter lead por ID |
| PATCH | `/{id}/status` | Atualizar status do lead |
| DELETE | `/{id}` | Deletar lead (soft delete) |

---

### 24. AuthController
**Base Path**: `/auth`  
**Total Endpoints**: 11

| Método HTTP | Caminho | Descrição |
|---|---|---|
| POST | `/register` | Registrar novo usuário |
| POST | `/login` | Login do usuário |
| GET | `/me` | Obter dados do usuário logado |
| GET | `/sessions` | Listar sessões ativas |
| DELETE | `/sessions/{sessionId}` | Revogar sessão específica |
| DELETE | `/sessions` | Revogar todas as sessões |
| POST | `/refresh` | Renovar token de acesso |
| POST | `/logout` | Logout do usuário |
| POST | `/change-password` | Alterar senha |
| POST | `/forgot-password` | Solicitar reset de senha |
| POST | `/reset-password` | Resetar senha |
| GET | `/debug` | Debug endpoint (desenvolvimento) |

---

### 25. AdminController
**Base Path**: `/admin`  
**Total Endpoints**: 5

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/overview` | Visão geral do sistema |
| GET | `/metrics/growth` | Métricas de crescimento |
| GET | `/metrics/cohorts` | Análise por cohort |
| GET | `/metrics/forecast` | Previsão de MRR |
| GET | `/metrics/health/{vendorId}` | Saúde de um vendor |

---

### 26. AdminAuditController
**Base Path**: `/admin/audit`  
**Total Endpoints**: 2

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/security` | Logs de auditoria de segurança |
| GET | `/vendor` | Logs de auditoria de vendors |

---

### 27. BillingAdminController
**Base Path**: `/api/v1/admin/billing`  
**Total Endpoints**: 4

| Método HTTP | Caminho | Descrição |
|---|---|---|
| GET | `/webhook-events` | Listar eventos de webhook |
| GET | `/webhook-events/{eventId}` | Detalhes de um evento |
| PUT | `/webhook-events/{eventId}/retry` | Reprocessar evento (retry) |
| GET | `/webhook-stats` | Estatísticas de webhooks |

---

### 28. BillingController
**Base Path**: `/billing`  
**Total Endpoints**: 8

| Método HTTP | Caminho | Descrição |
|---|---|---|
| POST | `/checkout` | Criar sessão de checkout |
| POST | `/webhook` | Processar webhook de billing |
| GET | `/subscription` | Obter subscription atual |
| GET | `/invoices` | Listar invoices |
| GET | `/invoices/{invoiceId}` | Detalhes de uma invoice |
| GET | `/payment-methods` | Listar métodos de pagamento |
| POST | `/payment-methods` | Adicionar método de pagamento |
| DELETE | `/payment-methods/{paymentMethodId}` | Remover método de pagamento |

---

## Análise por Método HTTP

| Método | Quantidade | Percentual |
|---|---|---|
| GET | 68 | 54.0% |
| POST | 37 | 29.4% |
| PUT | 11 | 8.7% |
| DELETE | 7 | 5.6% |
| PATCH | 3 | 2.4% |
| **TOTAL** | **126** | **100%** |

---

## Análise por Categoria

### Autenticação & Sessões
- AuthController: 11 endpoints

### Admin & Auditoria
- AdminController: 5 endpoints
- AdminAuditController: 2 endpoints
- BillingAdminController: 4 endpoints
Total: 11 endpoints

### Billing & Pagamentos
- BillingController: 8 endpoints
- BillingDashboardController: 13 endpoints
- WebhookMetricsController: 4 endpoints
- WebhookAlertController: 9 endpoints
- FailureAnalysisController: 8 endpoints
- WebhookReplayController: 6 endpoints
- WebhookFailedEventController: 1 endpoint
Total: 49 endpoints

### Vendors & Leads
- VendorController: 4 endpoints
- VendorLeadController: 13 endpoints
- LeadController: 5 endpoints
- LeadStatusHistoryController: 2 endpoints
Total: 24 endpoints

### Configurações & Usuários
- UserController: 4 endpoints
- SettingController: 6 endpoints
- PublicSettingController: 1 endpoint
- AdminSettingController: 3 endpoints
- RoleController: 2 endpoints
- UsageController: 2 endpoints
Total: 18 endpoints

### IA & Analytics
- AiController: 7 endpoints
- DashboardController: 1 endpoint
Total: 8 endpoints

### Webhooks Externos
- SendGridWebhookController: 1 endpoint
- CaktoWebhookController: 1 endpoint
- StripeWebhookController: 1 endpoint
- PaymentController: 1 endpoint
Total: 4 endpoints

### Utilitários
- FileController: 1 endpoint
Total: 1 endpoint

---

## Total Geral

**Total de Controllers**: 28  
**Total de Endpoints**: 126

Por categoria:
- Autenticação: 11 endpoints
- Admin: 11 endpoints
- Billing: 49 endpoints
- Vendors/Leads: 24 endpoints
- Configurações/Usuários: 18 endpoints
- IA/Analytics: 8 endpoints
- Webhooks Externos: 4 endpoints
- Utilitários: 1 endpoint

**Total**: 126 endpoints REST

---

*Relatório gerado através de análise automática dos arquivos Controller Java do projeto LeadFlow Backend*
