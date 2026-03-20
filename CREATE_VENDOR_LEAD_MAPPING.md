# 📍 MAPEAMENTO: POST /vendor-leads/leads

## 🔍 Fluxo Completo do Endpoint

```
POST /vendor-leads/leads
├─ Authorization: Bearer $JWT ✅ (required)
├─ X-Tenant-Id: public ✅ (required)
└─ Body: CreateLeadRequest (JSON)
    ├─ nomeCompleto (required, string, max 100)
    ├─ whatsapp (required, regex: ^[0-9+\-() ]{8,20}$)
    ├─ tipoConsorcio (optional, string, max 50)
    ├─ valorCredito (optional, string, max 50)
    └─ urgencia (optional, enum: quero_fechar|analisando|pesquisando)
```

---

## 📝 Validações / Checagens

### 1️⃣ **JwtAuthenticationFilter**
- Valida o JWT token do header Authorization
- Extrai email, userId, role, tenant
- Cria Authentication object no SecurityContext
- ❌ Se falhar aqui: **HTTP 401**

### 2️⃣ **VendorLeadController.createLead()**

```java
@PostMapping("/leads")
public ResponseEntity<?> createLead(
        @Valid @RequestBody CreateLeadRequest request) {
    
    // CHECK 1: SubscriptionGuard.resolveAccess()
    if (subscriptionGuard.resolveAccess() != SubscriptionAccessLevel.FULL) {
        return ResponseEntity.status(403).body(
            Map.of(
                "error", "SUBSCRIPTION_READ_ONLY",
                "message", "Assinatura não permite criar leads."
            )
        );
    }
    
    // CHECK 2: ensureVendorExists()
    // ├─ Obtém email do Authentication
    // ├─ Verifica ROLE_VENDOR
    // ├─ Se não existe Vendor: VendorService.createVendor()
    // │  ├─ Cria Vendor entity
    // │  └─ VendorRepository.save()
    // └─ Loga sucesso/erro (mas NÃO lança exception)
    ensureVendorExists();
    
    // CHECK 3: VendorLeadService.create(request)
    // ├─ @Transactional
    // ├─ Obtém email do SecurityContext
    // ├─ VendorService.ensureVendorExists(email)
    // ├─ VendorContext.getCurrentVendor().getId()
    // │  └─ ⚠️ AQUI PODE FALHAR COM 401
    // └─ Cria e salva VendorLead
    VendorLead createdLead = service.create(request);
    
    return ResponseEntity.ok(createdLead);
}
```

---

## 🔴 Possíveis Erros (401)

### Cenário 1: JWT Inválido/Expirado
```
JwtAuthenticationFilter
  └─ Token validation FAIL
      └─ HTTP 401: "Não Autorizado"
```

### Cenário 2: ROLE_VENDOR não atribuído
```
ensureVendorExists()
  ├─ hasVendorRole = false
  └─ ❌ Silentemente ignorado (catch bloco só loga)
     └─ VendorLeadService.create()
        └─ VendorContext.getCurrentVendor()
           └─ Vendor não encontrado
              └─ Lança UnauthorizedException
                  └─ HTTP 401
```

### Cenário 3: Vendor não foi criado
```
ensureVendorExists()
  ├─ VendorService.createVendor()
  ├─ VendorRepository.save()
  └─ @Transactional commitado (OK)
     └─ VendorLeadService.create()
        └─ findFirstByUserEmailIgnoreCase()
           ├─ PRECISA de commit anterior
           └─ ❓ Race condition?
```

### Cenário 4: SubscriptionGuard bloqueia (HTTP 403, não 401)
```
subscriptionGuard.resolveAccess()
  └─ FAIL
      └─ HTTP 403: "SUBSCRIPTION_READ_ONLY"
```

---

## 📋 Request Esperado (CORETO)

```json
POST /vendor-leads/leads
Content-Type: application/json
Authorization: Bearer eyJhbGciOiJIUzI1NiJ9...
X-Tenant-Id: public

{
  "nomeCompleto": "Maria Silva",
  "whatsapp": "11999999999",
  "tipoConsorcio": "VEICULO",
  "valorCredito": "100000",
  "urgencia": "quero_fechar"
}
```

---

## ✅ Response Esperado (HTTP 200)

```json
{
  "id": "uuid-xxx",
  "vendorId": "uuid-yyy",
  "nomeCompleto": "Maria Silva",
  "whatsapp": "11999999999",
  "tipoConsorcio": "VEICULO",
  "valorCredito": "100000",
  "urgencia": "quero_fechar",
  "stage": "NOVO",
  "score": 50,
  "createdAt": "2026-03-19T19:33:00Z"
}
```

---

## 🔍 Debug: O Que Está Falhando (401)

**Análise:**
1. ✅ JWT token está sendo ACEITO (outros endpoints funcionam)
2. ✅ Autenticação está OK (Get User funciona)
3. ✅ ROLE_VENDOR está sendo atribuído (teste registra com @leadflow.dev)
4. ❌ Mas `/vendor-leads/leads` retorna 401

**Hipótese Mais Provável:**
- VendorContext.getCurrentVendor() NÃO está encontrando o Vendor
- Mesmo com os fixes anteriores, a transação pode não estar sincronizando

**Por Quê?**
- `ensureVendorExists()` cria o Vendor (OK)
- Mas `VendorLeadService.create()` é em outra transação
- Quando tenta buscar com `findFirstByUserEmailIgnoreCase()`, Vendor pode não estar visível yet
- Lança UnauthorizedException que vira 401

---

## ✨ Solução Necessária

### Option A: Remover transação nested
Garantir que tudo corra na MESMA transação:
```java
@PostMapping("/leads")
@Transactional  // ← ADICIONAR
public ResponseEntity<?> createLead(...) {
    subscriptionGuard.resolveAccess(); // Checagem apenas
    vendorService.ensureVendorExists(email);  // Cria se não existe
    VendorLead lead = service.create(request);  // Usa vendor criado
    return ResponseEntity.ok(lead);
}
```

### Option B: Verificar race condition
Adicionar retry ou sleep:
```java
vendorService.ensureVendorExists(email);
Thread.sleep(100);  // Garante commit anterior
VendorLead lead = service.create(request);
```

### Option C: Passar Vendor ID explicitamente
Não depender de VendorContext:
```java
Vendor vendor = vendorService.ensureVendorExists(email);
VendorLead lead = service.createWithVendor(request, vendor.getId());
```
