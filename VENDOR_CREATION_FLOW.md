# 🗺️ MAPEAMENTO: Processo de Criação de Vendor

## 1️⃣ FASE 1: User Registration (Passo 2 do Teste)

### Endpoint
`POST /auth/register`

### Fluxo:
```
RegisterRequest (email, name, password)
    ↓
AuthController.register()
    ↓
AuthService.registerUser(name, email, password, tenant)
    ↓ 
Cria User entity:
  - id (UUID)
  - email
  - name
  - password (bcrypted)
  - createdAt
    ↓
Atribui Role ao User:
  IF email ends with "@leadflow.dev" OR "@email.com"
    → Assign ROLE_VENDOR
  ELSE
    → Assign ROLE_USER
    ↓
UserRepository.save(user)
    ↓
gera JwtToken com:
  - sub: email
  - userId: user.getId()
  - role: ROLE_VENDOR
  - tenant: public
    ↓
Retorna AuthResponse(accessToken, refreshToken)
```

### Estado do Banco Após Fase 1:
```sql
-- ✅ User CRIADO com ROLE_VENDOR
SELECT * FROM users 
WHERE email = 'test-20260319191132@leadflow.dev';

-- ❌ Vendor NÃO FOI CRIADO
SELECT * FROM vendors 
WHERE user_email = 'test-20260319191132@leadflow.dev';
```

---

## 2️⃣ FASE 2: Vendor Auto-Creation (Passo 7 do Teste)

### Endpoint
`POST /vendor-leads/leads`

### Fluxo:
```
POST /vendor-leads/leads com:
  Authorization: Bearer $JWT_TOKEN
  X-Tenant-Id: public
  Body: { nomeCompleto, whatsapp, urgencia, ... }
    ↓
JwtAuthenticationFilter
    ├─ Valida JWT Token
    ├─ Extrai email do JWT (sub)
    ├─ Cria Authentication object
    └─ Adiciona à SecurityContextHolder
    ↓
VendorLeadController.createLead()
    ├─ Chama: ensureVendorExists()
    │   ├─ Obtém email do Authentication
    │   ├─ Checa ROLE_VENDOR
    │   ├─ Se Vendor NÃO existe:
    │   │   └─ VendorService.createVendor(email)
    │   │       ├─ Cria Vendor entity:
    │   │       │   - id (UUID)
    │   │       │   - userEmail (normalizado)
    │   │       │   - nomeVendedor (parte antes do @)
    │   │       │   - whatsappVendedor ("0000000000")
    │   │       │   - slug (generated)
    │   │       │   - subscriptionStatus (TRIAL)
    │   │       └─ VendorRepository.save()
    │   └─ Se Vendor já existe: usa existente
    ↓
VendorLeadService.create(request)
    ├─ VendorContext.getCurrentVendor()  ⚠️ AQUI ESTÁ O PROBLEMA!
    │   ├─ Obtém Authentication
    │   ├─ Extrai email
    │   └─ Busca: findFirstByUserEmailIgnoreCase(email)
    │       └─ Se não encontra: lança UnauthorizedException (401)
    └─ Cria VendorLead
```

---

## 🔴 O PROBLEMA: 401 no Passo 7

### Root Cause
Quando `VendorContext.getCurrentVendor()` é chamado em `VendorLeadService.create()`:

1. **VendorLeadController.ensureVendorExists()** cria o Vendor ✅
2. **Mas** a criação acontece em uma transação separada
3. **VendorLeadService.create()** tenta buscar o Vendor IMEDIATAMENTE
4. **O commit da transação anterior pode não ter terminado**
5. **VendorRepository.findFirstByUserEmailIgnoreCase()** não encontra o Vendor recém-criado
6. **Lança: UnauthorizedException("Authenticated user does not belong to any vendor")** ❌
7. **HTTP 401 é retornado** ❌

### Diagrama de Timing:
```
Thread 1 (ensureVendorExists):
  VendorService.createVendor() ────────┐
    Inside @Transactional              │ Transaction
    VendorRepository.save()             │ in progress
                                        │
Thread 2 (VendorLeadService.create):
  VendorContext.getCurrentVendor()     │
    findFirstByUserEmailIgnoreCase()    │ Busca ANTES
    ❌ Não encontra!                    │ do commit
                                        │
                                        └── Commit (late!)
```

---

## ✅ SOLUÇÃO: Corrigir a Sincronização

### Option 1: Usar `ensureVendorExists()` retorno
```java
@PostMapping("/leads")
public ResponseEntity<?> createLead(...) {
    // Garante que Vendor existe
    Vendor vendor = vendorService.ensureVendorExists(email);
    
    // ANTES de chamar service que usa VendorContext
    vendorContext.setCurrentVendor(vendor); // ← Não existe!
    
    VendorLead createdLead = service.create(request);
    return ResponseEntity.ok(createdLead);
}
```

### Option 2: Passar Vendor ID na request
```java
@PostMapping("/leads")
public ResponseEntity<?> createLead(...) {
    vendorService.ensureVendorExists(email);
    
    // Buscar de novo, APÓS transação fazer commit
    Vendor vendor = vendorService.ensureVendorExists(email);
    
    VendorLead createdLead = service.create(request, vendor.getId());
    return ResponseEntity.ok(createdLead);
}
```

### Option 3: Combinar em um único @Transactional (MELHOR)
```java
@PostMapping("/leads")
public ResponseEntity<?> createLead(...) {
    VendorLead lead = vendorLeadService.createWithVendorEnsurance(request);
    return ResponseEntity.ok(lead);
}

// Em VendorLeadService:
@Transactional
public VendorLead createWithVendorEnsurance(CreateLeadRequest request) {
    String email = getCurrentUserEmail();
    
    // Cria Vendor se não existe (TUDO na mesma transação)
    vendorService.ensureVendorExists(email);
    
    // Agora busca o Vendor (ainda na mesma transação)
    UUID vendorId = vendorContext.getCurrentVendor().getId();
    
    // Cria Lead
    return create(request);
}
```

---

## 📋 Estado Atual vs Estado Esperado

### ATUAL (QUEBRADO):
```
1. Register User ✅
   └─ Cria User com ROLE_VENDOR ✅
   └─ Cria JWT com role=ROLE_VENDOR ✅

2. Create Vendor Lead ❌ (401)
   └─ JwtFilter valida token ✅
   └─ ensureVendorExists() cria Vendor ✅
   │  └─ VendorRepository.save() 
   │  └─ @Transactional em progresso...
   └─ VendorLeadService.create() tenta buscar ❌
      └─ VendorContext.getCurrentVendor()
      └─ VendorRepository.findFirstByUserEmailIgnoreCase()
      └─ NÃO ENCONTRA (transação anterior ainda não commitou)
      └─ Lança UnauthorizedException (501) ❌
```

### ESPERADO (CORRETO):
```
1. Register User ✅
   └─ Cria User com ROLE_VENDOR ✅
   └─ Cria JWT com role=ROLE_VENDOR ✅

2. Create Vendor Lead ✅
   └─ JwtFilter valida token ✅
   └─ VendorLeadService.create() com @Transactional
      └─ ensureVendorExists() cria Vendor
      └─ findFirstByUserEmailIgnoreCase() ENCONTRA
      └─ Retorna VendorLead criado ✅
```

---

## 🔧 Correção Necessária

### Modificar `VendorLeadService.create()`

**ANTES (ATUAL):**
```java
@Audit(...)
@CheckQuota(...)
public VendorLead create(CreateLeadRequest request) {
    // ❌ PROBLEMA: VendorContext busca sem garantia de sincronização
    UUID vendorId = vendorContext.getCurrentVendor().getId();
    
    VendorLead lead = new VendorLead();
    lead.setVendorId(vendorId);
    // ... resto da lógica
}
```

**DEPOIS (CORRETO):**
```java
@Audit(...)
@CheckQuota(...)
@Transactional  // ← ADICIONAR @Transactional
public VendorLead create(CreateLeadRequest request) {
    String email = getCurrentUserEmail(); // Extract from SecurityContext
    
    // ✅ Garante Vendor existe, DENTRO da mesma transação
    Vendor vendor = vendorService.ensureVendorExists(email);
    UUID vendorId = vendor.getId(); // Use retorno, não VendorContext
    
    VendorLead lead = new VendorLead();
    lead.setVendorId(vendorId);
    // ... resto da lógica
}
```

---

## 📊 Resumo do Fluxo Correto

```
┌─────────────────────────────────────────────────────────┐
│ POST /auth/register                                     │
│ ├─ AuthController.register()                            │
│ ├─ AuthService.registerUser()                           │
│ ├─ Cria User com ROLE_VENDOR                            │
│ └─ Retorna: {accessToken, refreshToken} ✅              │
└─────────────────────────────────────────────────────────┘
                         ↓
┌─────────────────────────────────────────────────────────┐
│ POST /vendor-leads/leads (com Authorization)            │
│ ├─ JwtAuthenticationFilter valida token ✅              │
│ ├─ VendorLeadController.createLead()                    │
│ │  ├─ @Transactional {                                  │
│ │  │   ├─ vendorService.ensureVendorExists()            │
│ │  │   │  └─ VendorRepository.save(vendor)              │
│ │  │   ├─ vendorLeadService.create()                    │
│ │  │   │  ├─ vendorService.ensureVendorExists() ✅      │
│ │  │   │  └─ VendorLeadRepository.save()                │
│ │  │   └─ [COMMIT]                                      │
│ │  └─ }                                                  │
│ └─ Retorna: {id, vendor_id, ...} ✅                     │
└─────────────────────────────────────────────────────────┘
```
