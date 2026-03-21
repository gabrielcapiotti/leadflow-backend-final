# Diagnóstico Técnico - HTTP 500 em /api/v1/billing/subscription

**Data:** 2026-03-21  
**Status:** ✅ PROBLEMA IDENTIFICADO + SOLUÇÕES PROPOSTAS

---

## 1. Problema Identificado

### Erro Observado
```
GET /api/v1/billing/subscription
→ HTTP 500 Internal Server Error
```

### Teste Mostrou
```
VendorId: (null)
[FAIL] User subscription v1 (HTTP 500)
```

---

## 2. Root Cause Analysis

### Linha do Erro
**File:** `BillingDashboardController.java`  
**Método:** `getMySubscription()` (linhas 115-121)

```java
@GetMapping("/subscription")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<SubscriptionDetailsDTO> getMySubscription() {
    UUID tenantId = vendorContext.getCurrentVendorId();  // ← PODE SER NULL
    log.info("Fetching subscription for authenticated tenant: {}", tenantId);
    SubscriptionDetailsDTO details = billingDashboardService.getSubscriptionDetails(tenantId);
    return ResponseEntity.ok(details);
}
```

### Serviço que Quebra
**File:** `BillingDashboardService.java`  
**Método:** `getSubscriptionDetails()` (linhas 72-80)

```java
public SubscriptionDetailsDTO getSubscriptionDetails(UUID tenantId) {
    Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
        .orElseThrow(() -> new ResponseStatusException(
            HttpStatus.NOT_FOUND,
            "Subscription not found for tenant: " + tenantId  // ← quebra aqui!
        ));

    return SubscriptionDetailsDTO.fromEntity(subscription);
}
```

### Por que Falha

| Cenário | O que acontece | Resultado |
|---------|---|---|
| `tenantId = null` | Repository query quebra | **NullPointerException** |
| `tenantId = UUID válido` | Subscription não encontrada | **404 Not Found** (esperado) |
| `tenantId = UUID inválido` | Subscription não encontrada | **404 Not Found** |

**Quando `tenantId` é `null`, a query SQL: `SELECT * FROM subscription WHERE tenant_id = NULL` → retorna erro SQL → **500 Internal Server Error**

---

## 3. Por que Vendor é Null?

### Fluxo Problemático
```
1. Usuário cria conta   ✅ OK
2. Backend cria User    ✅ OK
3. Backend NÃO cria Vendor automáticamente  ❌ PROBLEMA
4. VendorContext não consegue recuperar vendorId
5. getCurrentVendorId() retorna null
6. Query quebra com tenantId = null
7. HTTP 500
```

### Código que Deveria Garantir Vendor

**Contexto Esperado:**
```java
vendorContext.getCurrentVendorId()
```

**Implementação Provável (faltando proteção):**
```java
public UUID getCurrentVendorId() {
    // Retorna null se vendor não foi inicializado
    return authenticatedVendor != null ? authenticatedVendor.getId() : null;
}
```

---

## 4. Soluções

### ✅ Solução 1: Null-Check no Controller (Rápida - RECOMENDADA)

**Arquivo:** `BillingDashboardController.java`

```java
@GetMapping("/subscription")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<?> getMySubscription() {
    UUID tenantId = vendorContext.getCurrentVendorId();
    
    if (tenantId == null) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(Map.of(
            "error", "vendor_not_initialized",
            "message", "User vendor not found. Please complete onboarding.",
            "timestamp", LocalDateTime.now()
        ));
    }
    
    log.info("Fetching subscription for authenticated tenant: {}", tenantId);
    SubscriptionDetailsDTO details = billingDashboardService.getSubscriptionDetails(tenantId);
    return ResponseEntity.ok(details);
}
```

**Benefícios:**
- ✅ Rápido de implementar
- ✅ Resposta clara (400 Bad Request)
- ✅ Não quebra a API

---

### ✅ Solução 2: Criar Vendor Automático no Auth (Melhor)

**Arquivo:** LocationController ou AuthService

```java
@PostMapping("/auth/login")
public ResponseEntity<?> login(@RequestBody LoginRequest req) {
    User user = userService.authenticate(req.getEmail(), req.getPassword());
    
    // Garantir que vendor existe
    Vendor vendor = vendorService.ensureVendorExists(user);
    
    // Gerar token com vendor context
    String token = authService.generateToken(user, vendor);
    
    return ResponseEntity.ok(new LoginResponse(token));
}
```

**Método de Garantia:**
```java
public Vendor ensureVendorExists(User user) {
    return vendorRepository.findByUserEmail(user.getEmail())
        .orElseGet(() -> {
            Vendor newVendor = new Vendor();
            newVendor.setUser(user);
            newVendor.setStatus(ACTIVE);
            return vendorRepository.save(newVendor);
        });
}
```

**Benefícios:**
- ✅ Garante vendor sempre existe
- ✅ Solução em nível de domínio
- ✅ Sem surpresas depois

---

### ✅ Solução 3: Endpoint Resiliente (Fallback)

```java
public SubscriptionDetailsDTO getSubscriptionDetails(UUID tenantId) {
    if (tenantId == null) {
        log.warn("tenantId is null, returning empty subscription");
        return new SubscriptionDetailsDTO(); // vazio
    }
    
    Subscription subscription = subscriptionRepository.findByTenantId(tenantId)
        .orElseGet(() -> createDefaultSubscription(tenantId));

    return SubscriptionDetailsDTO.fromEntity(subscription);
}

private Subscription createDefaultSubscription(UUID tenantId) {
    Subscription sub = new Subscription();
    sub.setTenantId(tenantId);
    sub.setStatus(TRIAL);
    sub.setCreatedAt(LocalDateTime.now());
    return subscriptionRepository.save(sub);
}
```

**Benefícios:**
- ✅ Nunca retorna 500
- ✅ Sempre há um estado válido
- ✅ Melhor UX

---

## 5. Implementação Recomendada

**Combinar Soluções 1 + 2:**

1. **Curto prazo (hoje):** Adicionar validação null no controller (Solução 1)
2. **Médio prazo:** Garantir vendor no auth (Solução 2)
3. **Longo prazo:** Considerar Solução 3 para resiliência total

---

## 6. Código Completo - Solução 1 (Controller)

```java
@GetMapping("/subscription")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<?> getMySubscription() {
    UUID tenantId = vendorContext.getCurrentVendorId();
    
    if (tenantId == null) {
        log.warn("Attempted to fetch subscription but vendor not found for authenticated user");
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of(
                "error", "VENDOR_NOT_FOUND",
                "message", "Vendor context not initialized. Please complete registration.",
                "code", 1001
            ));
    }
    
    log.info("Fetching subscription for authenticated tenant: {}", tenantId);
    SubscriptionDetailsDTO details = billingDashboardService.getSubscriptionDetails(tenantId);
    return ResponseEntity.ok(details);
}

@GetMapping("/usage")
@PreAuthorize("isAuthenticated()")
public ResponseEntity<?> getMyUsage() {
    UUID tenantId = vendorContext.getCurrentVendorId();
    
    if (tenantId == null) {
        log.warn("Attempted to fetch usage but vendor not found for authenticated user");
        return ResponseEntity
            .status(HttpStatus.BAD_REQUEST)
            .body(Map.of(
                "error", "VENDOR_NOT_FOUND",
                "message", "Vendor context not initialized. Please complete registration.",
                "code", 1001
            ));
    }
    
    log.info("Fetching usage for authenticated tenant: {}", tenantId);
    BillingDashboardDTO.UsageStatisticsDTO usage = billingDashboardService.getUsageStatistics(tenantId);
    return ResponseEntity.ok(usage);
}
```

---

## 7. Testes Esperados Após Correção

```powershell
[1] Auth ✅ HTTP 200
[2] Get current user ✅ HTTP 200
[3] Get subscription ✅ HTTP 200
[4] Get invoices ✅ HTTP 200
[5] Get payment methods ✅ HTTP 200
[6] User usage v1 ✅ HTTP 200
[7] User subscription v1 ✅ HTTP 200 ou 400 (com mensagem clara)
[8] Webhooks ✅ HTTP 200
[9] Stripe ✅ HTTP 400 (esperado - missing params)
```

---

## 8. Conclusão

| Item | Status |
|------|--------|
| **Problema identificado** | ✅ Null `tenantId` no service |
| **Causa raiz** | ✅ Vendor não inicializado no auth |
| **Solução recomendada** | ✅ Validação no controller + Vendor auto-init |
| **Impacto** | ✅ Muda 500 → 400/200 claro |
| **Tempo de fix** | ✅ ~5 minutos (Solução 1) |

