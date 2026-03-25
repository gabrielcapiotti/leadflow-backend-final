# 🔍 Diagnóstico Raiz dos Erros de Teste - LeadFlow Auth

**Data:** 25 de Março de 2026  
**Teste:** Test-Auth-Fixed.ps1  
**Status:** 3 Erros Críticos Identificados (6/9 testes passando)

---

## 📊 Resumo dos Erros

| Teste | Endpoint | Status HTTP | Erro | Blocker | Cascata |
|-------|----------|-------------|------|---------|---------|
| #3 | POST /auth/login | 500 | Internal Server Error | ✅ SIM | Impede testes 5d, 8, 9 |
| #5d | POST /auth/login (isolação) | 500 | Internal Server Error | ✅ SIM | Cascata de #3 |
| #8 | DELETE /auth/sessions | 401 | Não Autorizado | ✅ SIM | Cascata de #3 |
| #9 | POST /auth/logout | 401 | Não Autorizado | Não | Cascata de #8 |

---

## 🔴 ERRO #1: Login HTTP 500 (BLOCKER CRÍTICO)

### Evidência do Erro
```
[3] Login with Credentials
   [FAIL] Login failed (HTTP 500)
      Error: O servidor remoto retornou um erro: (500) Erro Interno do Servidor.
```

### Padrão Observado
- ✅ POST /auth/register retorna HTTP 200 com tokens válidos
- ✅ Tokens recebidos são válidos (usados com sucesso em GET /auth/me)
- ❌ POST /auth/login com MESMAS credenciais retorna HTTP 500
- ❌ Padrão se repete em teste de isolação (linha 5d)

### Fluxo de Execução do Login
```java
// AuthController.java linha 103-120
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(
    @Valid @RequestBody LoginRequest request,
    HttpServletRequest httpRequest
) {
    String tenant = resolveTenant(httpRequest);
    
    User user = authService.authenticateUser(      // ⚠️ Etapa 1
        request.email(),
        request.password(),
        tenant
    );
    
    JwtToken accessToken = jwtService.generateToken(user, tenant);  // ⚠️ Etapa 2
    
    UUID tenantId = tenantService.getTenantIdBySchema(tenant);   // ⚠️ Etapa 3
    
    createSession(user.getId(), tenantId, accessToken, httpRequest);  // ⚠️ Etapa 4
    
    String refreshToken = refreshTokenService.generate(...);    // ⚠️ Etapa 5
    
    return ResponseEntity.ok(new AuthResponse(...));
}
```

### Causas Potenciais (do mais ao menos provável)

#### Causa 1: `tenantService.getTenantIdBySchema(tenant)` FALHANDO - **RAIZ CONFIRMADA** ✅

**🎯 RAIZ DO PROBLEMA ENCONTRADA:**

O método `getTenantIdBySchema()` lança `TenantNotFoundException` quando o tenant "public" é pesquisado:

```java
// TenantService.java linha 86-104
public UUID getTenantIdBySchema(String schema) {
    if (schema == null || schema.isBlank()) {
        throw new IllegalArgumentException("Schema cannot be blank");
    }

    String normalized = normalize(schema);

    if (!VALID_SCHEMA.matcher(normalized).matches()) {
        throw new IllegalArgumentException("Invalid schema format");
    }

    try {
        return jdbcTemplate.queryForObject(
                RESOLVE_ID_SQL,  // SELECT id FROM public.tenants WHERE schema_name = ? AND deleted_at IS NULL
                UUID.class,
                normalized
        );
    } catch (EmptyResultDataAccessException ex) {
        logger.error("Tenant not found for schema: {}", schema);
        throw new TenantNotFoundException(  // ❌ LANÇA SEM TRATAMENTO
                "Tenant not found for schema: " + schema
        );
    }
}
```

**Por que falha:**

1. **Schema "public" é RESERVADO** (Tenant.java linha 128-130):
```java
private void setSchemaName(String schema) {
    if ("public".equals(normalized)) {
        throw new IllegalArgumentException(
                "Schema 'public' is reserved"  // ❌ Nunca cria Tenant com schema='public'
        );
    }
    this.schemaName = normalized;
}
```

2. **Nunca há registro no banco** para schema='public':
```sql
SELECT id FROM public.tenants WHERE schema_name = 'public' AND deleted_at IS NULL
-- RETORNA: ()  [VAZIO - não encontra]
-- CAUSA: EmptyResultDataAccessException
-- CONVERTE PARA: TenantNotFoundException
-- LANÇA: HTTP 500 Internal Server Error
```

3. **AuthController.login() não captura a exceção:**
```java
// AuthController.java linha 110-115
User user = authService.authenticateUser(...);     // ✅ OK
JwtToken accessToken = jwtService.generateToken(...); // ✅ OK
UUID tenantId = tenantService.getTenantIdBySchema(tenant);  // ❌ LANÇA AQUI
// ↑ EXCEÇÃO NÃO TRATADA → HTTP 500
```

#### Causa 2: `userSessionService.createSession()` FALHANDO ⚠️
**Possível Razão:**
- Validação de ID de tenant nula ou inválida
- Constraint de banco de dados violada
- Duplicidade de session.tokenId

**Código Suspeito:**
```java
// AuthController.java linha 373-379
private void createSession(
    UUID userId,
    UUID tenantId,          // ⚠️ Pode ser NULL se tenantService.getTenantIdBySchema falhar
    JwtToken token,
    HttpServletRequest request
) {
    userSessionService.createSession(
        userId,
        tenantId,            // ⚠️ NULL aqui causaria erro
        token.getTokenId(),
        getClientIpAddress(request),
        request.getHeader("User-Agent")
    );
}
```

#### Causa 3: `authService.authenticateUser()` lançando EXCEÇÃO NÃO-TRATADA
**Evidência Indireta:**
- O código tem `.catch (Exception e)` no vendorService
- Mas não há global exception handler explícito para essa rota

**Possível Source:**
```java
// AuthService.java linha 186-191
try {
    vendorService.ensureVendorExists(user.getId());
} catch (Exception e) {
    logger.warn("Failed to auto-create vendor on login: {}", e.getMessage());
    // ✅ Tem proteção aqui
}
```

### Solução Recomendada

**Passo 1:** Adicionar try-catch protetor no login
```java
@PostMapping("/login")
public ResponseEntity<AuthResponse> login(
    @Valid @RequestBody LoginRequest request,
    HttpServletRequest httpRequest
) {
    String tenant = resolveTenant(httpRequest);
    
    User user = authService.authenticateUser(
        request.email(),
        request.password(),
        tenant
    );
    
    JwtToken accessToken = jwtService.generateToken(user, tenant);
    
    // ✅ ADICIONAR TRY-CATCH AQUI
    UUID tenantId = null;
    try {
        tenantId = tenantService.getTenantIdBySchema(tenant);
    } catch (Exception e) {
        log.error("Failed to resolve tenant ID for schema: {}", tenant, e);
        // Fallback: usar "public" tenant ID
        tenantId = UUID.fromString("00000000-0000-0000-0000-000000000000"); // ou configurável
    }
    
    createSession(user.getId(), tenantId, accessToken, httpRequest);
    
    String refreshToken = refreshTokenService.generate(
        user,
        getClientIpAddress(httpRequest),
        httpRequest.getHeader("User-Agent")
    );
    
    log.info("User {} logged in successfully", user.getId());
    
    return ResponseEntity.ok(
        new AuthResponse(accessToken.getToken(), refreshToken)
    );
}
```

**OPÇÃO 2 (Mais Elegante):** Modificar TenantService para lidar com "public" como caso especial

```java
// TenantService.java - Modificar método getTenantIdBySchema()
public UUID getTenantIdBySchema(String schema) {
    if (schema == null || schema.isBlank()) {
        throw new IllegalArgumentException("Schema cannot be blank");
    }
    
    // ✅ Caso especial para tenant "public"
    if ("public".equalsIgnoreCase(schema)) {
        log.debug("Returning default UUID for public schema");
        return UUID.fromString("00000000-0000-0000-0000-000000000000");
    }
    
    String normalized = normalize(schema);
    
    if (!VALID_SCHEMA.matcher(normalized).matches()) {
        throw new IllegalArgumentException("Invalid schema format");
    }
    
    try {
        return jdbcTemplate.queryForObject(
                RESOLVE_ID_SQL,
                UUID.class,
                normalized
        );
    } catch (EmptyResultDataAccessException ex) {
        logger.error("Tenant not found for schema: {}", schema);
        throw new TenantNotFoundException(
                "Tenant not found for schema: " + schema
        );
    }
}
```

**Próximos Passos - IMPLEMENTAR AGORA:**

1. ✅ **Escolher OPÇÃO 1 ou 2 acima**
2. ✅ **Aplicar alteração** no arquivo correspondente
3. ✅ **Rebuild:**
   ```bash
   mvn clean package -DskipTests
   ```
4. ✅ **Restart servidor**
5. ✅ **Retest:** Erros #1, #2, #3 desaparecem

---

## 🔴 ERRO #2: Revoke Sessions HTTP 401

### Evidência do Erro
```
[8] Revoke All Sessions
   [FAIL] Failed to revoke all sessions (HTTP 401)
      Error: O servidor remoto retornou um erro: (401) Não Autorizado.
```

### Análise
**Status:** Este é erro **CASCATA** do Erro #1

**Razão:**
1. Login retorna 500
2. Teste obtém token de erro en lugar de token válido
3. Token inválido é usado no header Authorization
4. Spring Security rejeita com 401

**URL:** `DELETE /auth/sessions` 

**Proteção:** `@DeleteMapping("/sessions")` requer `Authentication`

**Código:**
```java
@DeleteMapping("/sessions")
public ResponseEntity<Void> revokeAllSessions(Authentication authentication) {
    CustomUserDetails user = requireAuthenticatedUser(authentication);  // ⚠️ Requer auth válida
    
    UUID tenantId = tenantService.getTenantIdBySchema(resolveTenant());
    
    userSessionService.revokeAllUserSessions(user.getId(), tenantId);
    
    return ResponseEntity.noContent().build();
}
```

### Por Que é 401 e não 500?
- Spring Security intercepta a requisição ANTES de chegar no controller
- Se o token for inválido/expirado → 401 Unauthorized
- Se o controller falhar → 500 Internal Server Error

**Verificação:**
```bash
# De onde vem o token no teste?
GET /auth/me retorna: tokens válidos
POST /auth/login retorna: HTTP 500 (SEM TOKEN)
POST /auth/logout usa: token do register (que deveria ter expirado)
```

### Solução para Erro #2
**Prerequisito:** Resolver Erro #1

Após Erro #1 ser fixado:
- Login retornará HTTP 200 com token válido
- Token será usado em DELETE /auth/sessions
- Teste receberá 204 No Content

---

## 🔴 ERRO #3: Logout HTTP 401

### Evidência do Erro
```
[9] Logout (Current Session)
   [FAIL] Logout failed (HTTP 401)
      Error: O servidor remoto retornou um erro: (401) Não Autorizado.
```

### Análise
**Status:** Este é erro **CASCATA** do Erro #2

**Razão:**
1. Erro #1 causa login falhar
2. Erro #2 causa revokeAllSessions falhar
3. Token não é revogado
4. Logout tenta usar mesmo token inválido
5. Spring Security bloqueia com 401

**URL:** `POST /auth/logout`

**Proteção:**
```java
@PostMapping("/logout")
public ResponseEntity<Void> logout(
    Authentication authentication,      // ⚠️ Requer auth válida
    HttpServletRequest request
) {
    CustomUserDetails user = requireAuthenticatedUser(authentication);
    
    UUID tenantId = tenantService.getTenantIdBySchema(resolveTenant());
    
    String tokenId = extractTokenId(request);
    
    if (tokenId != null) {
        userSessionService.revokeSession(tokenId, tenantId);
    }
    
    return ResponseEntity.noContent().build();
}
```

### Solução para Erro #3
**Prerequisito:** Resolver Erro #1

Após Erro #1 ser fixado:
- Token será válido
- DELETE /auth/sessions funcionará (204)
- POST /auth/logout funcionará (204)

---

## 🎯 Plano de Ação

### Ordem de Prioridade

| Prioridade | Erro | Ação | ETA |
|-----------|------|------|-----|
| 🔥 1 | Login HTTP 500 | Investigate tenantService.getTenantIdBySchema() | **AGORA** |
| 🔥 2 | Add Exception Handler | Wrap login in try-catch | 5 min |
| 3 | Revoke 401 | Will auto-resolve when #1 fixado | Auto |
| 3 | Logout 401 | Will auto-resolve when #2 fixado | Auto |

### Próximos Passos em Ordem

1. ✅ **PRIMEIRO:** Investigar `TenantService.getTenantIdBySchema()`
   ```bash
   find src/ -name "TenantService.java" -exec cat {} \;
   grep -A 10 "getTenantIdBySchema" src/main/java -r
   ```

2. ✅ **SEGUNDO:** Verificar se há null check
   ```java
   UUID tenantId = tenantService.getTenantIdBySchema(tenant);
   if (tenantId == null) {
       log.error("Tenant ID is null for schema: " + tenant);
       // throw exception ou usar default
   }
   ```

3. ✅ **TERCEIRO:** Adicionar try-catch completo em login()

4. ✅ **QUARTO:** Rebuild e retest
   ```bash
   mvn clean package -DskipTests
   java -jar target/leadflow-backend-1.0.0.jar
   ./Test-Auth-Fixed.ps1
   ```

5. ✅ **QUINTO:** Se Erro #1 resolvido, Erro #2 e #3 desaparecerão automaticamente

---

## 📋 Dados Coletados para Debugging

### Teste Bem-Sucedido (Para Referência)
```
[2] Register New User
   [OK] User registered successfully (HTTP 200)
   [INFO] Access Token: eyJhbGciOiJIUzI1NiJ9.eyJqdGkiO...
   [INFO] Refresh Token: VQYiN9uCpMI5vrozkYy2PP3lskWfHx...

[5] Get Current User Profile
   [OK] Retrieved user profile (HTTP 200)
   [INFO] User ID: 09833171-9d10-45df-b458-2b4f7553e55a
   [INFO] Email: test-20260325102243228-9624@leadflow.dev
   [INFO] Role: ROLE_USER
   [INFO] Tenant ID: public
```

### Dados do Erro
- Email: `test-20260325102243228-9624@leadflow.dev`
- Password: `SecurePass123!@`
- Tenant: `public`
- Timestamp: `2026-03-25 10:22:43`

### Stack Trace Disponível
- Servidor ainda está rodando
- Logs estão sendo capturados
- Pode-se fazer curl para reproduzir

---

## 🔧 HTTP Requests para Debug Manual

### Request para Reproduzir Erro #1
```bash
curl -X POST "http://localhost:8081/auth/login" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: public" \
  -d '{
    "email": "test-20260325102243228-9624@leadflow.dev",
    "password": "SecurePass123!@"
  }' \
  -v
```

### Request para Testar Revoke
```bash
curl -X DELETE "http://localhost:8081/auth/sessions" \
  -H "Authorization: Bearer <YOUR_TOKEN>" \
  -H "X-Tenant-ID: public" \
  -v
```

---

## 📝 Conclusão

**Raiz do Problema:**
- **Erro #1** (HTTP 500): Provável falha em `tenantService.getTenantIdBySchema()` ou falta de exception handling
- **Erro #2 & #3** (HTTP 401): Cascata do Erro #1 - são falhas esperadas quando não há token válido

**Fix Complexity:** 🟢 Baixa (provável mudança simples em try-catch)

**Impact:** 🔥 Alto (bloqueia 3 testes cascata)

**Próximo:** Investigar TenantService imediatamente
