# 📊 MAPEAMENTO DOS ARQUIVOS PS1 - SUCESSO vs FALHA

## ✅ ARQUIVOS QUE FUNCIONARAM (SUCESSO)

### 1. **test-leads-all-FIXED.ps1** ✅ RECOMENDADO
**Status:** ✅ Funciona completamente
**Padrão:** Correto - Login → Token → Headers → Testes
**Endpoints testados:**
- ✅ HEALTH CHECK
- ✅ REGISTER NEW USER
- ✅ LOGIN USER (extrai token)
- ✅ GET CURRENT USER PROFILE
- ✅ CREATE STANDARD LEAD
- ✅ GET LEAD BY ID
- ✅ UPDATE LEAD STATUS
- ✅ LIST LEADS
- ✅ DELETE LEAD
- ✅ CREATE VENDOR LEAD
- ✅ GET VENDOR LEAD BY ID
- ✅ LIST VENDOR LEADS
- ✅ UPDATE VENDOR LEAD STAGE
- ✅ DELETE VENDOR LEAD
- ✅ VALIDATE DELETION

**Por quê funciona:**
```powershell
# Linha 126-150: Extrai token corretamente
$response = Invoke-WebRequest -Uri $LoginUrl -Method Post ...
$data = $response.Content | ConvertFrom-Json
$LoginToken = $data.accessToken

# Linha 157-164: Seta headers com Bearer token
$Global:CurrentHeaders = @{
    "X-Tenant-Id" = $TenantHeader
    "Authorization" = "Bearer $LoginToken"  # ← TOKEN CORRETO
    "Content-Type" = "application/json"
}

# Depois usa esses headers em TODOS os testes
$response = Invoke-WebRequest -Uri "$url" -Headers $Global:CurrentHeaders
```

---

### 2. **test-leads-all-Oficial.ps1** ✅ SUCESSO
**Status:** ✅ Mesmo padrão do FIXED
**Diferença:** Comentários em português "Patterns Applied from: test-all-Settings-Oficial.ps1"
**Recomendação:** Use FIXED ao invés (mais moderno)

---

### 3. **Test-Auth-Oficial.ps1** ✅ SUCESSO
**Status:** ✅ Testa especificamente endpoints de auth
**Endpoints testados:**
- ✅ POST /auth/register
- ✅ POST /auth/login
- ✅ GET /auth/me
- ✅ GET /auth/sessions
- ✅ DELETE /auth/sessions/{sessionId}
- ✅ DELETE /auth/sessions (revoke all)
- ✅ POST /auth/refresh
- ✅ POST /auth/logout
- ✅ POST /auth/change-password
- ✅ POST /auth/forgot-password
- ✅ POST /auth/reset-password

**Por quê funciona:**
- Não precisa de tenant context para endpoints públicos de auth
- Endpoints de auth estão em `shouldNotFilter()` do TenantFilter
- Não usa SubscriptionGuard

---

### 4. **test-all-Settings-Oficial.ps1** ✅ SUCESSO
**Status:** ✅ Testa settings endpoints
**Endpoints testados:**
- ✅ PUT /settings (Create)
- ✅ GET /settings (Read)
- ✅ GET /settings/{id} (Read by ID)
- ✅ PUT /settings (Update)
- ✅ DELETE /settings (Delete)
- ✅ POST /settings/reset (Reset)
- ✅ GET /public/settings/{id} (Public Read)
- ✅ POST /api/settings (Admin Create)
- ✅ DELETE /api/settings (Admin Delete)

**Por quê funciona:**
```powershell
# Linha 47-74: Depois de login, extrai token
$loginBody = @{
    email = $testEmail
    password = $testPassword
} | ConvertTo-Json

$loginResp = Invoke-WebRequest -Uri $loginUrl -Method Post ...
$loginBody = $loginResp.Content | ConvertFrom-Json
$token = $loginBody.accessToken

# Linha 77-83: Seta headers com token
$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
    "X-Tenant-ID" = "public"
}
```

---

## ❌ ARQUIVOS QUE FALHARAM (PROBLEMAS)

### 1. **test-users-management.ps1** ❌ FALHA

**Status:** ❌ Falha nos testes integrados
**Problema:** Não testa endpoints corretamente

**Erros encontrados:**
```powershell
# Linha 92: Tenta acessar $global:testUsers[0].id
$id = $global:testUsers[0].id

# Problema: $testUsers pode estar vazio ou não ter 'id' property
```

**Fluxo problemático:**
```powershell
# Cria 2 usuários mas não armazena IDs
for ($i=1; $i -le 2; $i++) {
    $email = "user$i-$([DateTime]::Now.Ticks)@leadflow.com"
    $payload = @{
        email=$email
        password="Test@123456"
        name="User $i"
    } | ConvertTo-Json
    
    $res = Invoke-WebRequest "$server/auth/register" ...
    
    if ($res.StatusCode -eq 201) {
        $data = $res.Content | ConvertFrom-Json
        $global:testUsers += $data  # ← $data pode não ter 'id'?
        Pass "User $i created"
    }
}

# depois...
$id = $global:testUsers[0].id  # ← PODE FALHAR se não tem 'id'
```

**Solução:** Ver modelo de resposta do endpoint `/auth/register`:
- Retorna objeto com campo `id`?
- Ou retorna apenas `{ accessToken, refreshToken }`?

---

### 2. **test-health-simple.ps1**, **test-health-final.ps1**, **test-health-endpoint.ps1** ⚠️ PARCIAL

**Status:** ⚠️ Testam apenas health check
**Problema:** Não testam endpoints reais com autenticação

Estas apenas checam `/actuator/health` que é um endpoint público que não requer tenant.

---

### 3. **test-billing-Oficial.ps1**, **test-billing-20-endpoints.ps1** ⚠️ POSSÍVEL FALHA

**Status:** ⚠️ Pode falhar por tenant context

**Problema esperado:** 
```
POST /billing/webhook
    ↓
TenantFilter procura por token/header
    ↓
Se não tem ambos → 400/401
```

**Solução:** Verificar se:
1. Endpoint WebhookController está em `shouldNotFilter()`
2. Se não precisa de tenant, remover do TenantFilter
3. Ou providenciar header X-Tenant-Id

---

### 4. **test-vendor-Oficial.ps1**, **test-vendor-simple.ps1** ⚠️ POSSÍVEL FALHA

**Status:** ⚠️ Pode falhar por tenant context

**Problema:** VendorController precisa de:
```java
@RequestMapping(value = {"/vendors", "/api/vendors"})
public class VendorController {
    @GetMapping
    public ResponseEntity<...> list(...) // ← PRECISA DE AUTENTICAÇÃO
}
```

**Fluxo esperado de falha:**
1. GET /vendors sem token
2. TenantFilter não consegue resolver tenant
3. Retorna 400/401
4. Teste falha

**Solução:** Login primeiro, depois usar token

---

### 5. **test-endpoints-final.ps1** ⚠️ COMPLETO MAS PODE FALHAR

**Status:** ⚠️ Potencialmente com falhas

**Problema:** Endpoint naming inconsistency
```powershell
# Pode estar testando:
- /api/leads (correto)
- /leads (também correto)
- /api/vendor-leads
- /vendor-leads

Mas sem token correto → 400/401 em cada um
```

---

### 6. **test-admin-endpoints.ps1** ⚠️ LIKELY FALHA

**Status:** ⚠️ Requer role ADMIN

**Problema:**
```java
@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")  // ← REQUER ADMIN ROLE!
```

**Fluxo de falha:**
1. Testes assumem usuário tem role ADMIN
2. Mas usuário criado no setup pode não ter
3. Spring Security retorna 403 Forbidden
4. Testes falham

**Solução:** 
- Criar usuário com role ADMIN
- OU mudar a autorização

---

## 📋 PADRÃO CORRETO PARA NOVOS TESTES PS1

```powershell
# ====================================
# SETUP
# ====================================

# 1. Health check (sem token precisa)
$health = Invoke-WebRequest "$server/actuator/health"

# 2. Register user
$registerData = @{
    email = "test@example.com"
    password = "Test123!@"
    name = "Test User"
} | ConvertTo-Json

$registResp = Invoke-WebRequest "$server/auth/register" `
    -Method Post `
    -ContentType "application/json" `
    -Body $registerData `
    -Headers @{ "X-Tenant-Id" = "public" }  # ← IMPORTANTE PARA PUBLIC ENDPOINTS

# 3. Login (OBRIGATÓRIO PARA PROTEGIDOS)
$loginData = @{
    email = "test@example.com"
    password = "Test123!@"
} | ConvertTo-Json

$loginResp = Invoke-WebRequest "$server/auth/login" `
    -Method Post `
    -ContentType "application/json" `
    -Body $loginData `
    -Headers @{ "X-Tenant-Id" = "public" }  # ← TOKEN SERÁ PRA ESSE TENANT

# 4. Extrair token
$loginBody = $loginResp.Content | ConvertFrom-Json
$token = $loginBody.accessToken

# 5. Montar headers para TODOS os testes
$headers = @{
    "Authorization" = "Bearer $token"        # ← OBRIGATÓRIO
    "X-Tenant-Id" = "public"                 # ← BACKUP (JWT já tem)
    "Content-Type" = "application/json"
}

# ====================================
# TESTES
# ====================================

# Todos usam os headers
$response = Invoke-WebRequest "$server/api/leads" `
    -Method Get `
    -Headers $headers `
    -UseBasicParsing

$response = Invoke-WebRequest "$server/api/leads" `
    -Method Post `
    -Headers $headers `
    -Body $leadData `
    -UseBasicParsing

# etc...
```

---

## 🎯 RECOMENDAÇÃO FINAL

### Use este como TEMPLATE:
✅ **test-leads-all-FIXED.ps1** é o melhor modelo

### Para endpoints de Auth:
✅ **Test-Auth-Oficial.ps1**

### Para endpoints de Settings:
✅ **test-all-Settings-Oficial.ps1**

### Não use como template:
❌ test-users-management.ps1 (problemas de estrutura)
❌ test-health-*.ps1 (muito simples para usar como base)
❌ test-billing-*.ps1 (falta verificação de tenant)
❌ test-vendor-*.ps1 (pode falhar)

---

## 📝 RESUMO

| Arquivo | Status | Recomendação |
|---------|--------|--------------|
| test-leads-all-FIXED.ps1 | ✅ | USE COMO TEMPLATE |
| test-leads-all-Oficial.ps1 | ✅ | OK MAS OUTDATED |
| Test-Auth-Oficial.ps1 | ✅ | USE PARA AUTH |
| test-all-Settings-Oficial.ps1 | ✅ | USE PARA SETTINGS |
| test-users-management.ps1 | ❌ | REWRITE NEEDED |
| test-health-*.ps1 | ⚠️ | ONLY FOR HEALTH |
| test-billing-*.ps1 | ⚠️ | ADICIONE TENANT |
| test-vendor-*.ps1 | ⚠️ | ADICIONE TOKEN |
| test-admin-endpoints.ps1 | ⚠️ | PRECISA ADMIN ROLE |
