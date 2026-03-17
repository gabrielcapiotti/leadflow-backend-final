# ERROR MAPPING - LEADFLOW BACKEND

## 🔴 ERRO #1: X-Tenant-ID Required (400 BAD_REQUEST)

**Mensagem:**
```
Header 'X-Tenant-ID' ou 'X-Tenant' é obrigatório
```

**Origem:** [TenantFilter.java](src/main/java/com/leadflow/backend/multitenancy/filter/TenantFilter.java:95-102)

**Causa Root:**
```java
// LINE 40-44 (shouldNotFilter method)
protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    
    return path.equals("/auth/register")      // ❌ AQUI É O PROBLEMA
           || path.equals("/auth/login")      // ❌ NÃO FUNCIONA
           || path.equals("/auth/refresh")    // ❌ PATHS ESTÃO ERRADOS
}
```

**Por Quê?**

O método `shouldNotFilter()` faz comparação exata com paths como `/auth/register`, mas as requisições reais chegam com `/api/auth/register` porque:

1. A aplicação tem prefixo `/api` configurado globalmente
2. Os controllers têm `@RequestMapping("/auth")`
3. A requisição completa é `/api/auth/register`

**Fluxo do Erro:**
```
Cliente envia:
POST http://localhost:8081/api/auth/register
  ↓
TenantFilter.shouldNotFilter() compara:
  "/api/auth/register" != "/auth/register"  ❌ FALHA
  ↓
TenantFilter tenta validar tenant
  ↓
TenantResolver.resolveTenant() procura por header X-Tenant-ID
  ↓
Header não existe → ResponseStatusException(BAD_REQUEST)
  ↓
Client recebe 400 com mensagem: "Header 'X-Tenant-ID' é obrigatório"
```

**Logs Que Confirmam:**
```
2026-03-16T08:59:20.0161375-03:00 ERROR TenantContext - 
"Attempt to access tenant context but none is set"
path: "/api/auth/register"

2026-03-16T08:59:20.0191783-03:00 ERROR TenantFilter - 
"Unexpected error resolving tenant"
org.springframework.web.server.ResponseStatusException:
400 BAD_REQUEST "Header 'X-Tenant-ID' ou 'X-Tenant' é obrigatório"
```

**Fix Recomendado:**
```java
// Trocar PATH COMPARE EXATO por WILDCARD
protected boolean shouldNotFilter(HttpServletRequest request) {
    String path = request.getRequestURI();
    
    // Remove possível prefixo /api
    String cleanPath = path.startsWith("/api/") ? 
        path.substring(4) : path;
    
    return cleanPath.equals("/auth/register")
           || cleanPath.equals("/auth/login")
           || cleanPath.equals("/auth/refresh")
           || path.startsWith("/actuator")
           || path.startsWith("/health")
           || path.startsWith("/swagger")
           || path.startsWith("/v3/api-docs");
}
```

---

## 🔴 ERRO #2: Register Endpoint Returns 500

**Mensagem:**
```
500 Internal Server Error
{
  "status": 500,
  "error": "Internal Server Error",
  "message": "An unexpected error occurred",
  "timestamp": "2026-03-16T11:50:59Z"
}
```

**Origem:** [AuthController.java](src/main/java/com/leadflow/backend/controller/auth/AuthController.java:70-85)

**Quando ocorre?**
- Quando você adiciona o header `X-Tenant-ID: public` corretamente
- O endpoint passa da validação do TenantFilter
- Mas enquanto processa o registro do usuário

**Causa Provável - Múltiplas Possibilidades:**

### 2A: Tenant Not Found During Registration

```java
// AuthController.java
UUID tenantId = tenantService.getTenantIdBySchema(tenant);
// Se tenant "public" não existir no banco → NullPointerException ou Exception
```

**Logs Relevantes:**
```
"Tenant context empty. Using default schema: public"
"tenant='public'" → Mas "public" schema pode não existir no banco
```

### 2B: VendorService Initialization Issue

```java
// VendorController.java (lines 79-100)
trialService.initializeTrial(safeVendor);
Vendor savedVendor = repository.save(safeVendor);

// Initialize usage (pode falhar aqui)
usageService.initializeUsage(savedVendor.getId(), planService.getActivePlan());

// Initialize quota (pode falhar aqui)
quotaService.initializePlanLimits(savedVendor.getId());

// Enable trial features (pode falhar aqui)
trialService.enableTrialFeatures(savedVendor);
```

**Cenários:**
- `planService.getActivePlan()` retorna null
- `usageService` tenta inserir em tabela inexistente
- `quotaService` falha em constraint de FK
- `vendorFeatureService` referencia vendor inexistente

### 2C: Missing Defaults in Plan Table

```sql
-- Possível:
SELECT * FROM plans WHERE status = 'ACTIVE';
-- Retorna: (vazio)

-- Resulta em:
planService.getActivePlan() → null
usageService.initializeUsage(vendorId, null) → NullPointerException
```

---

## 🟡 ERRO #3: Billing Endpoints Return 500 (If Register Works)

**Cenário:** Se conseguir passar do register, tenta acessar `/api/billing/subscription`

**Possíveis Causas:**

### 3A: Stripe Integration Not Configured

```java
// StripeService.java (linha ~52)
if (stripeSecretKey == null || stripeSecretKey.isEmpty()) {
    logger.warn("Stripe secret key is not configured");
    throw new RuntimeException("Stripe not configured");
}
```

**Log Evidence:**
```
"Stripe secret key is not configured - Stripe integration will not be available"
```

### 3B: Missing Subscription Record

```java
// BillingController.java
Subscription subscription = subscriptionService.getSubscriptionByVendorId(vendorId)
    .orElseThrow(() -> new ResourceNotFoundException("No subscription found"));
    // ↑ Pode falhar se vendor criado sem subscription
```

### 3C: Stripe API Response Parsing Error

```java
StripeCollection<Invoice> invoices = Invoice.list(requestOptions);
// Se Stripe library não consegue parsear → Exception
```

---

## 📊 DEPENDENCY CHAIN FOR ERRORS

```
/api/auth/register Request
    ↓
TenantFilter.shouldNotFilter() ❌ ERRO #1 (Path mismatch)
    ↓ (If fix applied)
AuthController.register()
    ↓
AuthService.registerUser()
    ↓
UserRepository.save(user) [WORKS]
    ↓
TenantService.getTenantIdBySchema("public") ❌ ERRO #2A (Tenant not found)
    ↓ (If tenant exists)
VendorService/TrialService/UsageService init ❌ ERRO #2B (Service initialization)
    ↓ (If all works)
User created + Vendor created (but might be incomplete)
    ↓
GET /api/billing/subscription
    ↓
SubscriptionService.getSubscriptionByVendorId() ❌ ERRO #3B (No subscription)
    ↓
Stripe API calls ❌ ERRO #3A/3C (Stripe not configured or error)
```

---

## 🔧 PRIORITY FIXES

### Priority 1 (Critical - Blocks Everything)
**Fix TenantFilter path matching** 
- FILE: `src/main/java/com/leadflow/backend/multitenancy/filter/TenantFilter.java`
- LINE: 37-44
- Change from exact match to prefix-aware comparison
- Impact: Unblocks auth endpoints

### Priority 2 (High - Blocks User Creation)
**Ensure "public" tenant exists OR make it optional**
- FILE: `src/main/java/com/leadflow/backend/service/multitenancy/TenantService.java`
- Create "public" schema during DB initialization if not exists
- OR: Allow registration without pre-existing tenant schema

### Priority 3 (High - Blocks Vendor Creation)
**Initialize active plan in database**
- Run SQL: `INSERT INTO plans (name, status, ...) VALUES ('default', 'ACTIVE', ...)`
- OR: Make plan initialization lazy

### Priority 4 (Medium - Blocks Billing)
**Configure Stripe API Key**
- Set environment variable: `STRIPE_API_KEY=sk_test_...`
- OR: Mock Stripe for development

---

## ✅ VERIFICATION CHECKLIST

- [ ] Verify `/api/auth/register` path in TenantFilter is fixed
- [ ] Verify "public" schema exists in DB
- [ ] Verify active plan exists in DB
- [ ] Verify STRIPE_API_KEY is configured
- [ ] Test: POST /api/auth/register with X-Tenant-ID header
- [ ] Test: GET /api/billing/subscription with token
- [ ] Test: GET /api/billing/invoices with pagination
- [ ] Test: GET /api/billing/payment-methods

---

## 📝 NOTES

- The Stripe test key is configured per logs: "billingEnabled=false"
- SubscriptionService has a warning about billing disabled
- PlanInitializer is "temporarily disabled during boot"
- These disabled features suggest intentional testing setup
