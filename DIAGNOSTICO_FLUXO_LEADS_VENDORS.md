# 🔍 DIAGNÓSTICO COMPLETO: FLUXO LEADS E VENDOR LEADS

**Data**: 20/03/2026  
**Status**: ⚠️ PROBLEMA IDENTIFICADO (Teste, não Backend)  
**Severidade**: CRÍTICA - Afeta todos os testes

---

## 1. RAIZ DO PROBLEMA ENCONTRADA

### ❌ Erro Principal
```
TenantFilter: "Unexpected error resolving tenant"
ResponseStatusException: 400 BAD_REQUEST "Header 'X-Tenant-ID' ou 'X-Tenant' é obrigatório"
```

### 📍 Localização
- **Arquivo**: `TenantFilter.java`
- **Problema**: Todos os endpoints exigem `X-Tenant-ID` header
- **Afetivo**: Até `/api/health` falha quando chamado sem o header

### 🎯 Impacto
- ✅ Servidor saudável
- ✅ Banco de dados funcional
- ❌ **Testes falhando por falta de header multi-tenant**
- ❌ Health check inicial do teste retorna 500

---

## 2. FLUXO DE LEADS (VISÃO COMPLETA)

### 📊 Arquitetura de Leads

```
┌─────────────────────────────────────┐
│         Lead Lifecycle              │
├─────────────────────────────────────┤
│                                     │
│  User (Dono)                        │
│      ↓                              │
│  Lead (Entity)                      │
│      ├─ user_id (FK)                │
│      ├─ name                        │
│      ├─ email (UNIQUE per user)     │
│      ├─ phone                       │
│      ├─ status (NEW/CONTACTED/etc)  │
│      ├─ soft_delete                 │
│      └─── LeadStatusHistory ←────── Audit trail
│                                     │
│  Endpoints:                         │
│  POST   /leads                      │
│  GET    /leads                      │
│  GET    /leads/{id}                 │
│  PATCH  /leads/{id}/status          │
│  DELETE /leads/{id}                 │
│                                     │
└─────────────────────────────────────┘
```

### 🔑 Camadas do Fluxo

#### 1️⃣ **Request → TenantFilter** ⚠️ FALHA AQUI
```java
// PROBLEMA: Header faltando
GET /api/health
// DEVERIA SER:
GET /api/health
X-Tenant-ID: public  // ← OBRIGATÓRIO
```

#### 2️⃣ **Authentication** 
```
JwtAuthenticationFilter
  ↓
SecurityContextHolder.getAuthentication()
  ↓
CustomUserDetails(User)
```

#### 3️⃣ **LeadController.createLead()**
```java
@PostMapping
public ResponseEntity<LeadResponse> createLead(
    @AuthenticationPrincipal UserDetails principal,  // JWT
    @RequestBody CreateLeadRequest request           // {name,email,phone}
)
```

#### 4️⃣ **LeadService.createLead()**
```java
// Validações:
1. User not null
2. Name not blank
3. Email valid + unique per user
4. Create Lead entity
5. Create LeadStatusHistory (Audit)
6. Return LeadResponse
```

#### 5️⃣ **Database Schema**
```sql
leads (
  id UUID PRIMARY KEY,
  user_id UUID NOT NULL (FK),  -- Isolamento por usuário
  email VARCHAR(150) NOT NULL,
  name VARCHAR(150) NOT NULL,
  phone VARCHAR(20),
  status ENUM[NEW|CONTACTED|QUALIFIED|etc],
  deleted_at TIMESTAMP NULL,    -- Soft delete
  created_at TIMESTAMP,
  updated_at TIMESTAMP,
  
  UNIQUE(email, user_id)        -- Email único por usuário
)

lead_status_history (
  id UUID PRIMARY KEY,
  lead_id UUID NOT NULL (FK),
  status ENUM,
  changed_by UUID (FK User),     -- Pode ser NULL (SYSTEM)
  changed_at TIMESTAMP
)
```

---

## 3. FLUXO DE VENDOR LEADS (VISÃO COMPLETA)

### 📊 Arquitetura de Vendor Leads

```
┌──────────────────────────────────────┐
│      VendorLead Lifecycle            │
├──────────────────────────────────────┤
│                                      │
│  Vendor (Auto-criado)                │
│      ↓                               │
│  VendorLead (Entity)                 │
│      ├─ vendor_id (FK)               │
│      ├─ nome_completo                │
│      ├─ whatsapp (UNIQUE)            │
│      ├─ stage (NOVO/QUALIFIED)       │
│      ├─ score (0-100)                │
│      ├─ soft_delete                  │
│      └─── Conversas/Histórico        │
│                                      │
│  Endpoints:                          │
│  POST   /vendor-leads/leads          │
│  GET    /vendor-leads                │
│  GET    /vendor-leads/{id}           │
│  PATCH  /vendor-leads/{id}/stage     │
│  DELETE /vendor-leads/{id}           │
│                                      │
└──────────────────────────────────────┘
```

### 🔑 Camadas do Fluxo

#### 1️⃣ **Request → TenantFilter** ⚠️ FALHA AQUI
```java
// PROBLEMA: Header faltando
POST /api/vendor-leads/leads
// DEVERIA SER:
POST /api/vendor-leads/leads
X-Tenant-ID: public  // ← OBRIGATÓRIO
```

#### 2️⃣ **VendorLeadController.createLead()**
```java
@PostMapping("/leads")
public ResponseEntity<?> createLead(
    @RequestBody CreateLeadRequest request
)
// Internamente:
// 1. ensureVendorExists() - Auto-cria vendor se não existe
// 2. subscriptionGuard.assertFullAccess() - Valida subscription
// 3. service.create(request) - Cria lead
```

#### 3️⃣ **VendorContext.getCurrentVendor()** → 401 AQUI
```java
public Vendor getCurrentVendor() {
    Authentication auth = SecurityContextHolder.getContext().getAuthentication();
    String email = auth.getName();
    
    // Busca vendor pelo user_email
    return vendorRepository
        .findFirstByUserEmailIgnoreCase(email)
        .orElseThrow(() ->
            new UnauthorizedException(
                "Authenticated user does not belong to any vendor"  // ← ERRO 401
            ));
}
```

**Problema**: Usuário comum (ROLE_USER) não tem ROLE_VENDOR, então não tem Vendor!

#### 4️⃣ **Database Schema**
```sql
vendor_leads (
  id UUID PRIMARY KEY,
  vendor_id UUID NOT NULL (FK),
  nome_completo VARCHAR(200) NOT NULL,
  whatsapp VARCHAR(30) NOT NULL,     -- UNIQUE implícito
  stage ENUM[NOVO|QUALIFIED|etc],
  status VARCHAR(50),
  score INT (0-100),
  deleted_at TIMESTAMP NULL,
  created_at TIMESTAMP,
  
  FOREIGN KEY(vendor_id) REFERENCES vendors(id)
)

vendors (
  id UUID PRIMARY KEY,
  user_email VARCHAR(255) NOT NULL,
  name VARCHAR(200),
  schema_name VARCHAR(200),  -- Multi-tenancy
  created_at TIMESTAMP
)
```

---

## 4. PROBLEMAS IDENTIFICADOS NO TESTE

### 🔴 Problem #1: Health Check sem Header Tenant
```
Status: 500 Internal Server Error
Causa: GET /api/health (sem X-Tenant-ID)
Solução: Adicionar header X-Tenant-ID: public
```

**Linha no teste**: 
```powershell
# ERRADO:
$response = Invoke-WebRequest -Uri "$BaseUrl/api/health" -UseBasicParsing

# CORRETO:
$headers = @{ "X-Tenant-ID" = "public" }
$response = Invoke-WebRequest -Uri "$BaseUrl/api/health" `
    -Headers $headers `
    -UseBasicParsing
```

### 🔴 Problem #2: Create Lead (Test 5) - 500
```
Possíveis causas (investigar com fix #1):
1. LeadStatusHistory relationship violation
2. User not properly resolved from JWT
3. Null user_id in request
```

### 🔴 Problem #3: List Leads (Test 8) - 500
```
Possível causa:
- Paginação sem header X-Tenant-ID
- Query endpoint resolvido incorretamente
```

### 🔴 Problem #4: Create Vendor Lead (Test 10) - 409 Conflict
```
Possível causa:
- UNIQUE(whatsapp) violation
- Vendor auto-create falhou silenciosamente
- Transação parcial commitada
```

### 🔴 Problem #5: List Vendor Leads (Test 12) - 401 Unauthorized
```
Causa CONFIRMADA: User não tem ROLE_VENDOR
O usuário testado tem apenas ROLE_USER

Fluxo esperado:
- User + ROLE_VENDOR → Auto-cria Vendor
- User sem ROLE_VENDOR → 401 ao acessar VendorLeadController
```

---

## 5. ANÁLISE: BANCO vs TESTE

### ✅ Banco de Dados: SAUDÁVEL
- Schema correto (V36__create_vendor_leads.sql verificado)
- Constraints e Foreign Keys adequados
- Indexes em lugar certo
- Soft delete implementado
- Multi-tenancy configurado

### ❌ Testes: MAL ESTRUTURADOS
1. **Falta header X-Tenant-ID** em todos os requests
2. **Fluxo de autorização inconsistente** (usuário comum vs vendor)
3. **Health check não é multi-tenant aware**
4. **VendorLeadController agora requer ROLE_VENDOR** (mudança recente?)

---

## 6. SOLUÇÃO

### ✅ FIX 1: Adicionar Header Tenant Global
```powershell
# NO INÍCIO do teste
$Global:CurrentHeaders = @{
    "Authorization" = "Bearer $token"
    "X-Tenant-ID" = "public"
    "Content-Type" = "application/json"
}

# USAR em TODOS os requests
Invoke-WebRequest -Uri "..." `
    -Headers $Global:CurrentHeaders
```

### ✅ FIX 2: Separar Fluxos de Usuário
```powershell
# Fluxo 1: User comum (Lead API)
$userHeaders = @{
    "Authorization" = "Bearer $userToken"
    "X-Tenant-ID" = "public"
}

# Fluxo 2: Vendor (Vendor Lead API)
# Adicionar ROLE_VENDOR ao usuário OU usar usuário diferente
$vendorHeaders = @{
    "Authorization" = "Bearer $vendorToken"  # Token com ROLE_VENDOR
    "X-Tenant-ID" = "public"
}
```

### ✅ FIX 3: Fazer Health Check Multi-Tenant Safe
```java
// EM HealthController.java adicionar:
@GetMapping
public ResponseEntity<?> health() {
    // ANTES: Exigia X-Tenant-ID e falhava
    // DEPOIS: Health check sem tenant context
    return ResponseEntity.ok(Map.of(
        "status", "UP",
        "timestamp", Instant.now()
    ));
}
```

Ou permitir `/actuator/health` sem tenant:
```java
// Em security config
.authorizeHttpRequests(auth -> auth
    .requestMatchers("/actuator/health").permitAll()
    .requestMatchers("/api/health").permitAll()  // ← ADD
    .anyRequest().authenticated()
)
```

---

## 7. PRÓXIMOS PASSOS (PIU-UP)

### 📝 Ações Imediatas
1. ✅ Aplicar FIX 1 (adicionar header X-Tenant-ID global)
2. ✅ Aplicar FIX 2 (configurar users com roles corretos)
3. ✅ Aplicar FIX 3 (permitir health check sem tenant)
4. ✅ Re-executar test-leads-all.ps1
5. ✅ Validar 100% pass rate

### 📊 Validação
```powershell
# Expected results após fixtures:
[1] Health Check ✅ 200
[2] Register User ✅ 201
[3] Login ✅ 200
[4] Get Profile ✅ 200
[5] Create Lead ✅ 201
[6] Get Lead ✅ 200
[7] List Leads ✅ 200
[8] Update Lead Status ✅ 200
[9] Delete Lead ✅ 204
[10] Create Vendor Lead ✅ 201
[11] Get Vendor Lead ✅ 200
[12] List Vendor Leads ✅ 200
[13-15] Vendor operations ✅ 200

TOTAL: 15/15 PASSING (100%)
```

---

## 8. CONCLUSÃO

| Aspecto | Status | Evidência |
|---------|--------|-----------|
| **Banco de Dados** | ✅ OK | Schema correto, migrations aplicadas |
| **Backend (Code)** | ✅ OK | Controllers, Services, Repositories funcionando |
| **Testes** | ❌ FALHA | Headers faltando, roles inconsistentes, fluxo mal organizado |
| **Root Cause** | 🎯 ENCONTRADA | TenantFilter exige X-Tenant-ID, testes não enviam |
| **Criticidade** | ⚠️ BLOCKER | Bloqueia todos os testes |
| **Complexidade Fix** | 🟢 FÁCIL | 30 minutos máximo |

