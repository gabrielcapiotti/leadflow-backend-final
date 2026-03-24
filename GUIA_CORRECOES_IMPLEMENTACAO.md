# 🔧 GUIA DE CORREÇÃO - AÇÕES NECESSÁRIAS

## PROBLEMA #1: TenantContext Não Inicializado
**Arquivo:** `/src/main/java/com/leadflow/backend/multitenancy/filter/TenantFilter.java`  
**Linha:** 124-143  
**Status:** 🔴 CRÍTICO

### Causa
```java
String tenant = tenantResolver.resolveTenant(request);

if (tenant == null || tenant.isBlank()) {
    response.sendError(400, "Header 'X-Tenant-Id' is required");
    return;  // ← RETORNA SEM SETTAR TENANT
}

TenantContext.setTenant(tenant);  // ← NUNCA CHEGA AQUI
```

### Efeito
- TenantContext.getTenant() retorna NULL
- SubscriptionGuard.resolveVendorStrict() lança `AccessDeniedException`
- Endpoints autenticados retornam 403/500

### Ação de Correção
**VERIFICAR:** Qual endpoint está retornando erro 400?

```powershell
# Teste simples com headers completos
$headers = @{
    "Authorization" = "Bearer $token"
    "X-Tenant-Id" = "public"
    "Content-Type" = "application/json"
}

$response = Invoke-WebRequest "http://localhost:8081/api/leads" `
    -Method Get `
    -Headers $headers `
    -UseBasicParsing `
    -ErrorAction SilentlyContinue

Write-Host "Status: $($response.StatusCode)"
Write-Host "Body: $($response.Content)"
```

**SE retorna 400 "Header 'X-Tenant-Id' is required":**
- TenantResolver.resolveTenant() está retornando NULL
- VERIFY: JWT token está incluindo claim `tenant`?

---

## PROBLEMA #2: JWT Token Não Contém Tenant Claim
**Arquivo:** `/src/main/java/com/leadflow/backend/security/jwt/JwtService.java`  
**Método:** `extractTenant(token)`  
**Status:** 🔴 CRÍTICO

### Como diagnosticar
```java
// Adicione log no JwtService.java:
public String extractTenant(String token) {
    String tenant = extractClaim(token, claims -> claims.get("tenant", String.class));
    log.info("JWT Token tenant claim: {}", tenant);  // ← ISSO MOSTRA QUAL É O VALOR
    return tenant;
}
```

### Verificar no token
```powershell
# Após login, extrair token
$token = "eyJhbGc..."

# Decodificar (parte do meio entre os . são os claims em base64)
# Online: jwt.io ou decodar manualmente

# Se vir: { "tenant": null } ← PROBLEMA ENCONTRADO
# Se vir: { "tenant": "public" } ← ESTÁ OK
```

### Ação de Correção
**Verificar JwtService.generateToken():**

```java
// DEVE conter:
claims.put("tenant", tenantId);  // ← ESSA LINHA TEM?
```

Se não tem, ADICIONE:
```java
public String generateToken(User user, String tenantId) {
    Map<String, Object> claims = new HashMap<>();
    claims.put("subject", user.getEmail());
    claims.put("userId", user.getId().toString());
    claims.put("tenant", tenantId);  // ← ADICIONE ESSA LINHA
    claims.put("roles", user.getRoles().stream()
        .map(Role::getName)
        .toList());
    
    return createToken(claims, user.getEmail());
}
```

---

## PROBLEMA #3: SubscriptionGuard - Vendor Not Found
**Arquivo:** `/src/main/java/com/leadflow/backend/security/SubscriptionGuard.java`  
**Método:** `resolveVendorStrict()`  
**Linha:** 110-125  
**Status:** 🟡 POSSÍVEL

### Causa
```java
return vendorRepository
    .findByUserEmailAndTenantId(email, tenant)
    .stream()
    .findFirst()
    .orElseThrow(() -> {
        return new AccessDeniedException("Vendor not found for current tenant");
    });
```

### Efeito
Se vendor não existe → 403 Forbidden

### Ação de Correção

**VERIFICAR:** Vendor foi criado para o user?

```sql
-- No database, verificar:
SELECT * FROM vendors 
WHERE user_email = 'seu-email@example.com' 
AND tenant_id = 'public';

-- Se retorna VAZIO → Vendor não existe
-- Se retorna com dados → Vendor existe
```

**SE vendor não existe, criar:**
```sql
INSERT INTO vendors (
    id, name, user_email, tenant_id, 
    subscription_status, subscription_access_level,
    created_at, updated_at
)
VALUES (
    gen_random_uuid(),
    'Test Vendor',
    'seu-email@example.com',
    'public',
    'ACTIVE',
    'FULL',
    NOW(),
    NOW()
);
```

---

## PROBLEMA #4: UserController - PreAuthorize ADMIN Required
**Arquivo:** `/src/main/java/com/leadflow/backend/controller/user/UserController.java`  
**Linha:** 20  
**Status:** 🟡 ESPERADO

### Causa
```java
@RestController
@RequestMapping("/users")
@PreAuthorize("hasRole('ADMIN')")  // ← ADMIN ROLE OBRIGATÓRIA
public class UserController {
```

### Efeito
Se user não tem role ADMIN → 403 Forbidden

### Ação de Correção

**OPÇÃO 1: Verificar role do usuário**
```sql
-- Verificar roles do usuário logado:
SELECT u.email, r.name as role
FROM users u
INNER JOIN user_roles ur ON u.id = ur.user_id
INNER JOIN roles r ON ur.role_id = r.id
WHERE u.email = 'seu-email@example.com';

-- Se não retorna nada ou retorna role diferente de ADMIN
-- O usuário não tem permissão
```

**OPÇÃO 2: Dar role ADMIN ao usuário (temporário para testes)**
```sql
-- Encontrar role ADMIN
SELECT id FROM roles WHERE name = 'ADMIN';

-- Atribuir ao user (substitua <user_id> e <admin_role_id>)
INSERT INTO user_roles (user_id, role_id)
VALUES ('<user_id>', '<admin_role_id>')
ON CONFLICT DO NOTHING;
```

**OPÇÃO 3: Remover autorização (para testes)**
```java
// EM UserController.java - REMOVER ESSA LINHA:
// @PreAuthorize("hasRole('ADMIN')")

// Aplicar apenas em endpoints específicos:
@RestController
@RequestMapping("/users")
public class UserController {

    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")  // ← APENAS AQUI
    public ResponseEntity<Page<UserResponse>> list(Pageable pageable) {
        // ...
    }
}
```

---

## PROBLEMA #5: LeadController - Subscription Write Access
**Arquivo:** `/src/main/java/com/leadflow/backend/controller/lead/LeadController.java`  
**Método:** `enforceWriteAccess()`  
**Linha:** 165-172  
**Status:** 🟡 POSSÍVEL

### Causa
```java
private void enforceWriteAccess() {
    if (subscriptionGuard.resolveAccess() != SubscriptionAccessLevel.FULL) {
        throw new ResponseStatusException(
            HttpStatus.FORBIDDEN,
            "Subscription does not allow write operations"
        );
    }
}
```

### Efeito
POST/PUT/DELETE /leads retorna 403 se subscription não é FULL

### Ação de Correção

**VERIFICAR:** Subscription level do vendor

```sql
-- Verificar subscription
SELECT id, subscription_access_level, subscription_status
FROM vendors
WHERE user_email = 'seu-email@example.com'
AND tenant_id = 'public';

-- Deve retornar 'FULL' em subscription_access_level
-- Deve retornar 'ACTIVE' em subscription_status
```

**SE não for FULL, atualizar:**
```sql
UPDATE vendors
SET subscription_access_level = 'FULL',
    subscription_status = 'ACTIVE'
WHERE user_email = 'seu-email@example.com'
AND tenant_id = 'public';
```

---

## PROBLEMA #6: teste-users-management.ps1 - Estrutura Errada
**Arquivo:** `/test-users-management.ps1`  
**Linha:** 70-90  
**Status:** 🟡 QUEBRADO

### Causa
```powershell
for ($i=1; $i -le 2; $i++) {
    # ... cria user
    $res = Invoke-WebRequest ...
    
    if ($res.StatusCode -eq 201) {
        $data = $res.Content | ConvertFrom-Json
        $global:testUsers += $data  # ← PODE NÃO TER 'id' FIELD
        Pass "User $i created"
    }
}

# Depois:
$id = $global:testUsers[0].id  # ← PODE FALHAR
```

### Ação de Correção

**Não mexer nesse arquivo, use ao invés:**
✅ **test-leads-all-FIXED.ps1** (é muito melhor)

Ou reescrever test-users-management.ps1 para:")

```powershell
# NOVO MODELO
$global:testUsers = @()

for ($i=1; $i -le 2; $i++) {
    $email = "testuser$i-$timestamp@leadflow.dev"
    $payload = @{
        email = $email
        password = "SecurePass123!"
        name = "Test User $i"
    } | ConvertTo-Json

    try {
        $response = Invoke-WebRequest -Uri "$RegisterUrl" `
            -Method Post `
            -Headers @{ 
                "X-Tenant-Id" = $TenantHeader
                "Content-Type" = "application/json"
            } `
            -Body $payload `
            -UseBasicParsing `
            -ErrorAction Stop
        
        if ($response.StatusCode -eq 201) {
            $user = $response.Content | ConvertFrom-Json
            $global:testUsers += @{
                email = $email
                id = $user.id  # Garante que tem id
            }
            Write-Host "✅ Created user: $email with ID: $($user.id)"
        }
    } catch {
        Write-Host "❌ Failed to create user: $($_)"
    }
}

# Depois usar com segurança:
if ($global:testUsers.Count -gt 0) {
    $id = $global:testUsers[0].id
    # ... usar id
}
```

---

## CHECKLIST DE DIAGNÓSTICO

Execute isto em ordem:

### 1️⃣ Verificar Tenant Context Setup
```powershell
$headers = @{
    "X-Tenant-Id" = "public"
    "Content-Type" = "application/json"
}

# Deve retornar 200
$response = Invoke-WebRequest "http://localhost:8081/actuator/health" `
    -Headers $headers `
    -UseBasicParsing

Write-Host "Health Status: $($response.StatusCode)"
```

### 2️⃣ Registrar e Logar
```powershell
# Register
$email = "testuser-$(Get-Date -Format 'yyyyMMddHHmmssfff')@leadflow.dev"
$registerResp = Invoke-WebRequest "http://localhost:8081/auth/register" `
    -Method Post `
    -Headers @{ "X-Tenant-Id" = "public"; "Content-Type" = "application/json" } `
    -Body (@{ email = $email; password = "Test123!@"; name = "Test" } | ConvertTo-Json) `
    -UseBasicParsing

Write-Host "Register Status: $($registerResp.StatusCode)"

# Login
$loginResp = Invoke-WebRequest "http://localhost:8081/auth/login" `
    -Method Post `
    -Headers @{ "X-Tenant-Id" = "public"; "Content-Type" = "application/json" } `
    -Body (@{ email = $email; password = "Test123!@" } | ConvertTo-Json) `
    -UseBasicParsing

$loginData = $loginResp.Content | ConvertFrom-Json
$token = $loginData.accessToken
Write-Host "Login Status: $($loginResp.StatusCode)"
Write-Host "Token: $($token.Substring(0,30))..."
```

### 3️⃣ Testar Endpoint com Token
```powershell
$headers = @{
    "Authorization" = "Bearer $token"
    "X-Tenant-Id" = "public"
    "Content-Type" = "application/json"
}

# GET Leads (deve funcionar)
$leadsResp = Invoke-WebRequest "http://localhost:8081/api/leads" `
    -Headers $headers `
    -UseBasicParsing `
    -ErrorAction SilentlyContinue

Write-Host "GET /leads Status: $($leadsResp.StatusCode)"
if ($leadsResp.StatusCode -ne 200) {
    Write-Host "Error: $($leadsResp.Content)"
}
```

### 4️⃣ Se falhar, verificar logs
```bash
# Ver logs de erro no servidor
docker logs leadflow-backend-api  # ou seu container name

# Procurar por:
# - "Tenant context not resolved"
# - "Tenant mismatch"
# - "Vendor not found"
# - "Subscription"
```

---

## RESUMO DAS AÇÕES

| Problema | Arquivo | Ação | Prioridade |
|----------|---------|------|-----------|
| TenantContext NULL | TenantFilter.java | Verificar JWT claim `tenant` | 🔴 ALTA |
| JWT sem tenant | JwtService.java | Adicionar `claims.put("tenant", ...)` | 🔴 ALTA |
| Vendor not found | DB | INSERT vendor para user | 🟡 MÉDIA |
| User sem ADMIN role | DB | INSERT user_roles com ADMIN | 🟡 MÉDIA |
| Subscription BLOCKED | DB | UPDATE vendors SET ...FULL | 🟡 MÉDIA |
| test-users-management.ps1 | Arquivo | Use test-leads-all-FIXED.ps1 | 🟢 BAIXA |

---

## PRÓXIMOS PASSOS

1. ✅ Verifique a saída do checklist acima
2. ✅ Corrija os problemas na ordem de prioridade
3. ✅ Execute test-leads-all-FIXED.ps1 para validar
4. ✅ Se ainda falhar, ative logs DEBUG e envie output
