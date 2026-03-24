# 🎯 SUMÁRIO EXECUTIVO - DIAGNÓSTICO DOS TESTES

## 📊 VISÃO GERAL

```
❌ TESTES FALHANDO POR: Tenant Context não inicializado
└─ Máquina: SubscriptionGuard.resolveVendorStrict()
   └─ Causa raiz: TenantContext.getTenant() retorna NULL
      └─ Motivo: TenantFilter não consegue resolver tenant
         └─ Raiz: JWT token não contém claim 'tenant'
            └─ Solução: Verificar JwtService.generateToken()
```

---

## 🔴 ERRO PRINCIPAL

**Quando testa, recebe:**
```
403 Forbidden - AccessDeniedException: Tenant context not resolved
```

**Stack trace esperado:**
```
com.leadflow.backend.security.SubscriptionGuard.resolveVendorStrict()
  → TenantContext.getTenant() returns NULL
  → Throws: "Tenant context not resolved"

Endpoints afetados:
  ❌ POST /api/leads (create)
  ❌ GET /api/leads (list)
  ❌ GET /api/leads/{id}
  ❌ PATCH /api/leads/{id}/status
  ❌ DELETE /api/leads/{id}
  ❌ POST /api/vendor-leads/leads
  ❌ GET /api/vendor-leads
  ❌ (Todos que usam SubscriptionGuard)
```

---

## 🔍 FLUXO DE ERRO DETALHADO

```
Requisição: POST /api/leads
    ↓
Spring Security Authentication
    ↓
TenantFilter.doFilterInternal()
    ↓
tenantResolver.resolveTenant(request)
    ↓
    ├─ Step 1: Procura JWT no header Authorization
    │  ↓
    │  JwtService.extractTenant(token)
    │  ↓
    │  ❌ Falha: claims não tem "tenant" field
    │  ↓
    │  Retorna: NULL
    │
    └─ Step 2: Procura Header X-Tenant-Id
       ↓
       ✅ Encontra: "public"
       ↓
       Retorna: "public"
    ↓
TenantContext.setTenant("public")  ✅
    ↓
FilterChain continua
    ↓
LeadController.createLead(@AuthenticationPrincipal principal)
    ↓
enforceWriteAccess()
    ↓
subscriptionGuard.resolveAccess()
    ↓
resolveVendorStrict()
    ↓
String tenant = TenantContext.getTenant()
    ↓
    ⚠️ PROBLEMA: Em outro thread/context, TenantContext pode estar diferente
    ↓
    Se NULL → ❌ AccessDeniedException("Tenant context not resolved")
    Se "public" → vendorRepository.findByUserEmailAndTenantId(email, "public")
                  ↓
                  Se vazio → ❌ AccessDeniedException("Vendor not found")
                  Se encontra → ✅ Sucesso
```

---

## 📋 CHECKLIST - O QUE JÁ FOI TESTADO E FUNCIONA

### ✅ FUNCIONANDO CORRETAMENTE
- **test-leads-all-FIXED.ps1** - 15 endpoints de leads + vendors ✅
- **Test-Auth-Oficial.ps1** - 11 endpoints de autenticação ✅
- **test-all-Settings-Oficial.ps1** - 9 endpoints de settings ✅

### ❌ FALHANDO OU COM PROBLEMAS
- **test-users-management.ps1** - Estrutura errada
- **test-health-*.ps1** - Muito simples, não valida autenticação
- **test-billing-*.ps1** - Falta tenant context
- **test-vendor-*.ps1** - Falta token/tenant
- **test-admin-endpoints.ps1** - Requer role ADMIN

---

## 🎓 O QUE FUNCIONA vs NÃO FUNCIONA

### ✅ PADRÃO QUE FUNCIONA (test-leads-all-FIXED.ps1)
```powershell
# 1. Health check (público - sem token)
→ ✅ Sucesso

# 2. Register novo user
→ ✅ Sucesso (endpoint público com header X-Tenant-Id)

# 3. Login → Extrai token
→ ✅ Sucesso (Bearer token agora disponível)

# 4. Create headers com token
$headers = @{
    "Authorization" = "Bearer $token"  # ← TOKEN AQUI
    "X-Tenant-Id" = "public"
    "Content-Type" = "application/json"
}
→ ✅ Pronto para usar

# 5. Todos os outros testes usam esses headers
→ ✅ Sucesso (TenantContext inicializado, JWT válido)
```

### ❌ PADRÃO QUE FALHA (test-users-management.ps1)
```powershell
# 1. Health check
→ ✅ Sucesso

# 2. Auth setup
→ ✅ Sucesso

# 3. Create users loop
→ ❓ Problema: $global:testUsers[$i].id pode não existir

# 4. Testa GET /users

# Mas falta o bearer token nos headers!
# Sem token → TenantFilter falha
→ ❌ 400/401 Bad Request
```

---

## 💡 ROOT CAUSE ANALYSIS (5 PORQUÊS)

**1. Por que os testes falham?**
   → Recebem 403 Forbidden "Tenant context not resolved"

**2. Por que TenantContext.getTenant() retorna NULL?**
   → Porque TenantFilter.doFilterInternal() não chama `TenantContext.setTenant()`

**3. Por que TenantFilter não chama setTenant()?**
   → Porque `tenantResolver.resolveTenant()` retorna NULL e o filtro faz `return;` sem setar

**4. Por que tenantResolver retorna NULL?**
   → Porque JWT não tem claim "tenant" ou não está sendo enviado como "Bearer <token>"

**5. Por que JWT não tem claim "tenant"?**
   → Porque JwtService.generateToken() não está adicionando esse claim ao JWT

---

## 🔧 SOLUÇÃO EM 3 PASSOS

### Passo 1: Verificar JwtService
```java
// File: src/main/java/com/leadflow/backend/security/jwt/JwtService.java

public String generateToken(User user, String tenantId) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("subject", user.getEmail());
    claims.put("userId", user.getId().toString());
    claims.put("tenant", tenantId);  // ← ESSA LINHA TEM? SE NÃO, ADICIONAR!
    claims.put("roles", ...);
    return createToken(claims, user.getEmail());
}
```

### Passo 2: Verificar Vendor existe
```sql
-- No database:
SELECT * FROM vendors 
WHERE user_email = 'seu-usuario@example.com' 
AND tenant_id = 'public';

-- Se vazio, inserir:
INSERT INTO vendors (id, name, user_email, tenant_id, subscription_access_level)
VALUES (gen_random_uuid(), 'Test', 'seu-usuario@example.com', 'public', 'FULL');
```

### Passo 3: Executar teste correto
```powershell
# Use este arquivo:
.\test-leads-all-FIXED.ps1
# Ele já tem o padrão correto implementado
```

---

## 📈 MATRIZ DE ERROS vs CAUSA

| HTTP Status | Mensagem | Causa | Solução |
|-------------|----------|-------|---------|
| 400 | "Header 'X-Tenant-Id' is required" | TenantResolver retorna NULL | Adicionar header ou JWT com claim |
| 401 | "Missing tenant identification..." | Mesmo de cima | Idem |
| 403 | "Tenant context not resolved" | TenantContext.getTenant() = NULL | Verificar TenantFilter setup |
| 403 | "Vendor not found for current tenant" | Vendor não existe no DB | Inserir vendor |
| 403 | "Subscription does not allow write..." | Subscription level não é FULL | UPDATE vendor set ...FULL |
| 403 | "Subscription inactive" | Subscription status é BLOCKED | UPDATE vendor set ...ACTIVE |

---

## 🎯 RECOMENDAÇÃO IMEDIATA

### USAR ESTE TEMPLATE:
```powershell
# Copie de test-leads-all-FIXED.ps1
# Modifique apenas os URLs/emails conforme necessário
# Não invente padrão novo - o fornecido funciona
```

### NÃO USE:
```powershell
# test-users-management.ps1 (estrutura incompleta)
# Crie novo a partir de test-leads-all-FIXED.ps1 se precisar
```

---

## 📊 EVIDÊNCIA: POR QUE ALGUNS TESTES FUNCIONAM

**test-leads-all-FIXED.ps1 funciona porque:**

```
Linha 119: ✅ $TenantHeader = "public"
          Seta tenant padrão

Linha 125-150: ✅ makeLogin()
          Cria usuário → Faz login → Extrai token JWT

Linha 157-164: ✅ $Global:CurrentHeaders = @{
                    "Authorization" = "Bearer $LoginToken"  ← TOKEN_AQUI
                    "X-Tenant-Id" = "public"
                    "Content-Type" = "application/json"
                }
          Monta headers corretamente

Linhas 175+: ✅ Invoke-WebRequest -Uri "$url" -Headers $Global:CurrentHeaders
          TODOS os testes usam headers com token
          
          ✅ TenantFilter executa
          ✅ tenantResolver extrai tenant do JWT
          ✅ TenantContext.setTenant("public") é chamado
          ✅ SubscriptionGuard consegue resolver vendor
          ✅ Sucesso!
```

---

## 🚀 PRÓXIMA AÇÃO

1. Verifique o item `Passo 1: Verificar JwtService` acima
2. Se `claims.put("tenant", tenantId)` não existe: **ADICIONE**
3. Verifique o item `Passo 2: Verificar Vendor existe`
4. Se não existe na DB: **INSIRA**
5. Execute: `.\test-leads-all-FIXED.ps1`
6. Deve retornar: ✅ PASS RATE: 100%

---

## 📞 SE AINDA NÃO FUNCIONAR

Ative logs DEBUG e colete:
```
1. HTTP Status code da requisição que falha
2. Response body/error message
3. Server logs (docker logs leadflow-backend-api)
4. Procure por palavras-chave:
   - "Tenant context"
   - "Vendor not found"
   - "Subscription"
   - "Access denied"
```

Então compartilhe os logs para análise adicional.
