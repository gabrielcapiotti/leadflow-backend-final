# 🔍 DIAGNÓSTICO RAIZ DOS ERROS DOS TESTES

## ERRO RAIZ PRINCIPAL: Tenant Context Not Resolved

### 1. **Problema Principal**
```
SubscriptionGuard.resolveVendorStrict() → LINE 107-119
  TenantContext.getTenant() retorna NULL
  ↓
  Lança: AccessDeniedException("Tenant context not resolved")
```

### 2. **Por que TenantContext fica NULL?**

**O `TenantFilter` não está setando o tenant corretamente:**

```java
// TenantFilter.java - LINE 124
String tenant = tenantResolver.resolveTenant(request);

if (tenant == null || tenant.isBlank()) {
    response.sendError(400, "Header 'X-Tenant-Id' is required")
    return;  // ← AQUI RETORNA ERRO 400, NUNCA SETA O TENANT
}

TenantContext.setTenant(tenant); // ← NUNCA CHEGA AQUI
```

### 3. **Por que `tenantResolver.resolveTenant()` falha?**

**TenantResolver procura por:**
- **PASSO 1:** JWT Token no header `Authorization: Bearer <token>` 
- **PASSO 2:** Header `X-Tenant-Id` como fallback

```java
// TenantResolver.java - LINE 63-89
if (tenantFromJwt != null) {
    // ✅ Tem JWT? Use JWT como fonte de verdade
    return tenantFromJwt;
}

if (tenantFromHeader != null) {
    // ✅ Não tem JWT pero tem header? Use header
    return tenantFromHeader;
}

// ❌ Nenhum dos dois? Erro 401
throw new ResponseStatusException(
    HttpStatus.UNAUTHORIZED,
    "Missing tenant identification (JWT or X-Tenant-Id header required)"
);
```

---

## ERROS POR ENDPOINT

### ❌ Erro 1: `/leads` (LeadController)

**Fluxo de falha:**
1. Teste POST `/leads` para criar lead
2. TenantFilter não encontra JWT (token expirado/inválido)
3. TenantFilter procura por `X-Tenant-Id` header
4. Se header não for enviada → 400 Bad Request
5. Se header for enviada MAS JWT inválido → 401 Unauthorized
6. Se passar do filter, `SubscriptionGuard.resolveVendorStrict()` tenta:
   - `vendorRepository.findByUserEmailAndTenantId(email, tenant)`
   - Se vendor não existe para esse tenant → **AccessDeniedException**("Vendor not found")

**Código problemático (LeadController.java:56-60):**
```java
@PostMapping
public ResponseEntity<LeadResponse> createLead(
    @AuthenticationPrincipal UserDetails principal,
    @Valid @RequestBody CreateLeadRequest request
) {
    enforceWriteAccess();  // ← AQUI CHAMA subscriptionGuard.resolveAccess()
    // que chama resolveVendorStrict() que precisa do Tenant
```

---

### ❌ Erro 2: `/users` (UserController)

**Fluxo de falha:**
```
@PreAuthorize("hasRole('ADMIN')")  ← ESTÁ EM TODA A CLASS!
    ↓
Se usuário não tiver role ADMIN → 403 Forbidden
    ↓
Se não puder autenticar (JWT inválido) → 401 Unauthorized
    ↓
Se passar auth, TenantFilter fail → 400/401 depend do caso
```

**Código problemático (UserController.java:20):**
```java
@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")  // ← APLICA EM TODOS OS ENDPOINTS
public class UserController {
```

---

### ❌ Erro 3: `/vendor-leads` (VendorLeadController)

**Fluxo de falha:**
```
@PostMapping("/leads")
    ↓
subscriptionGuard.assertFullAccess();  ← LINE 56
    ↓
resolveVendorStrict()  ← TenantContext.getTenant() = NULL
    ↓
AccessDeniedException("Tenant context not resolved")
```

---

## RAIZ #1: JWT/Token Validation

**Problema:** Os testes criam token mas:
1. Token não está sendo enviado no header `Authorization: Bearer <token>`
2. OU token está sendo enviado MAS JwtService.extractTenant() falha
3. OU token é inválido/expirado

**Verificação necessária:**
```java
// Testar se JwtService.extractTenant() está funcionando
jwtService.extractTenant(token) // está retornando qual tenant?
```

---

## RAIZ #2: TenantContext Setup

**Problema:** O `TenantContext` nunca é inicializado porque:
1. O filtro falha antes de chegar em `TenantContext.setTenant()`
2. OU o filtro pula endpoints que precisam de tenant

```java
// TenantFilter.java - LINE 44-51
protected boolean shouldNotFilter(HttpServletRequest request) {
    boolean isPublicAuth = path.startsWith("/auth/register")
            || path.startsWith("/auth/login")
            || path.startsWith("/auth/refresh");  // ← ESSAS PULAM O FILTRO
    
    return isPublicAuth || isWebhook || isPublicApi || ...;
}
```

**O problema:** Endpoints que PRECISAM do tenant (como `/leads`, `/users`) NÃO estão pulando o filtro (correto), mas o filtro FALHA porque não consegue resolver tenant.

---

## RAIZ #3: SubscriptionGuard strictness

**Problema:** O `SubscriptionGuard.resolveVendorStrict()` é MUI rigoroso:

```java
// SubscriptionGuard.java - LINE 107-119
private Vendor resolveVendorStrict() {
    String tenant = TenantContext.getTenant();  // ← PODE SER NULL
    
    if (tenant == null || tenant.isBlank()) {
        throw new AccessDeniedException("Tenant context not resolved");
    }
    
    return vendorRepository.findByUserEmailAndTenantId(email, tenant)
        .stream()
        .findFirst()
        .orElseThrow(() -> {
            // Se vendor não existe pro tenant → outra exceção
            return new AccessDeniedException("Vendor not found for current tenant");
        });
}
```

Se `findByUserEmailAndTenantId` retorna vazio → **Vendor not found**

---

## RESUMO DOS ERROS

| Endpoint | Tipo | Problema | Status |
|----------|------|----------|--------|
| POST /leads | CREATE | TenantContext NULL ou Vendor not found | 400/401/403 |
| GET /leads | READ | TenantContext NULL ou Vendor not found | 400/401/403 |
| GET /leads/{id} | READ | TenantContext NULL ou Vendor not found | 400/401/403 |
| PATCH /leads/{id}/status | UPDATE | TenantContext NULL ou Vendor not found | 400/401/403 |
| DELETE /leads/{id} | DELETE | TenantContext NULL ou Vendor not found | 400/401/403 |
| GET /users | LIST | @PreAuthorize ADMIN + TenantContext NULL | 403/401 |
| POST /vendor-leads/leads | CREATE | TenantContext NULL | 400/401 |
| GET /vendor-leads | LIST | TenantContext NULL | 400/401 |

---

## SOLUÇÃO (próximos passos)

### 1. **Verificar JWT token**
- O token está sendo gerado corretamente?
- O token contém o claim `tenant`?
- O token está sendo enviado no header `Authorization: Bearer <token>`?

### 2. **Verificar TenantContext inicialização**
- O TenantFilter está sendo chamado?
- O tenantResolver está retornando um valor válido?
- O tenant está sendo setado antes de usar nos services?

### 3. **Verificar Vendor lookup**
- O vendor existe no banco para o usuário + tenant logado?
- A query `findByUserEmailAndTenantId(email, tenant)` está retornando result?

### 4. **Verificar autenticação**
- O usuário está sendo autenticado corretamente?
- O usuário tem role ADMIN?
- O username/email está correto?

---

## TESTES QUE FUNCIONAM (test-leads-all-FIXED.ps1)

**Por quê funcionam:**
✅ Fazem login primeiro: `POST /auth/login`
✅ Extraem token do response: `$data.accessToken`
✅ Setam header corretamente: `Authorization = "Bearer $LoginToken"`
✅ Setam tenant header: `X-Tenant-Id = $TenantHeader`
✅ Depois usam token nos testes

**Padrão correto:**
```powershell
# 1. LOGIN
$response = Invoke-WebRequest -Uri "$LoginUrl" -Method Post -Body $loginData

# 2. EXTRAIR TOKEN
$data = $response.Content | ConvertFrom-Json
$LoginToken = $data.accessToken

# 3. SETARHEADERS
$headers = @{
    "Authorization" = "Bearer $LoginToken"
    "X-Tenant-Id" = "public"
    "Content-Type" = "application/json"
}

# 4. USAR NOS TESTES
$response = Invoke-WebRequest -Uri "$url" -Headers $headers
```

---

## CONCLUSÃO

O erro raiz NÃO está nos endpoints, está em:
1. **JWT Token não está sendo extraído correctamente** (JwtService.extractTenant())
2. **TenantFilter falhando silenciosamente** antes de settar TenantContext
3. **SubscriptionGuard rejeitando vendor não encontrado**

Os testes que funcionam (test-leads-all-FIXED.ps1) provam que o código FUNCIONA quando:
- ✅ Token está sendo gerado 
- ✅ Token é enviado no header Authorization
- ✅ TenantContext é setado antes de usar
