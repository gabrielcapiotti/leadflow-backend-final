# Mapeamento Completo de Endpoints de Auth

## Resumo
**Implementados:** 9 endpoints + 1 dev-only = 10 endpoints
**Faltando:** 2 endpoints com infraestrutura pronta (veja abaixo)

---

## ⚠️ Endpoints FALTANDO (com infraestrutura pronta)

### 11. **POST /auth/forgot-password** (PLANEJADO)
**Público (sem autenticação)**
```
Method: POST
Content-Type: application/json
Auth Required: ❌ Não
```

**Status:**
- ✅ **PasswordResetService** implementado em `com.leadflow.domain.auth.service`
- ✅ **Tabela `password_reset_token`** criada no banco
- ❌ **Endpoint não exposto** no AuthController
- ❌ **JWT gerado mas não integrado ao fluxo**

**Request Body:**
```json
{
  "email": "user@example.com"
}
```

**Response (200 OK - por segurança, não revela se email existe):**
```json
{
  "message": "Se o email existe, você receberá um link para resetar a senha"
}
```

**Funcionalidade:**
- Busca usuário by email
- Se existir: gera token seguro (32 bytes, hash SHA-256)
- Envia email com link de reset (válido por 15 minutos)
- Retorna sempre 200 (anti-enumeração: não revela se email existe)
- Invalida tokens antigos do mesmo usuário

**Por que falta:**
- Service pronto mas nenhum controller expõe o endpoint
- DTOs ainda não criados (ForgotPasswordRequest)

---

### 12. **POST /auth/reset-password** (PLANEJADO)
**Público (sem autenticação)**
```
Method: POST
Content-Type: application/json
Auth Required: ❌ Não
```

**Status:**
- ✅ **PasswordResetService.resetPassword()** implementado
- ✅ **Validação e hash** prontos
- ❌ **Endpoint não exposto** no AuthController
- ❌ **DTOs não criados**

**Request Body:**
```json
{
  "token": "reset_token_from_email",
  "newPassword": "NewSecurePass123!@"
}
```

**Response (204 No Content):**
```
(sem corpo - sucesso silencioso)
```

**Funcionalidade:**
- Valida token (hash SHA-256)
- Verifica expiração (15 minutos)
- Verifica se token já foi usado
- Hash nova senha com BCrypt
- Invalida TODOS os tokens antigos do usuário (segurança)
- Revoga TODAS as sessões do usuário (força logout)

**Por que falta:**
- Service pronto mas nenhum controller expõe o endpoint
- DTOs ainda não criados (ResetPasswordRequest)

---

## 📋 Endpoints de Auth (Implementados)

### 1. **POST /auth/register**
**Público (sem autenticação)**
```
Method: POST
Content-Type: application/json
Auth Required: ❌ Não
```

**Request Body:**
```json
{
  "name": "string",
  "email": "string",
  "password": "string",
  "confirmPassword": "string"
}
```

**Response (201 Created):**
```json
{
  "accessToken": "jwt_token",
  "refreshToken": "refresh_token"
}
```

**Funcionalidade:**
- Cria novo usuário
- Retorna access token + refresh token
- Cria sessão automaticamente
- Disponível para tenants públicos

---

### 2. **POST /auth/login**
**Público (sem autenticação)**
```
Method: POST
Content-Type: application/json
Auth Required: ❌ Não
```

**Request Body:**
```json
{
  "email": "string",
  "password": "string"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "jwt_token",
  "refreshToken": "refresh_token"
}
```

**Funcionalidade:**
- Autentica usuário com email/password
- Retorna access token + refresh token
- Cria sessão automaticamente
- Registra IP e User-Agent

---

### 3. **GET /auth/me**
**Protegido (requer autenticação)**
```
Method: GET
Auth Required: ✅ Sim (Bearer Token)
Headers: Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
{
  "id": "uuid",
  "email": "string",
  "role": "ROLE_VENDOR|ROLE_USER|ROLE_ADMIN"
}
```

**Funcionalidade:**
- Retorna dados do usuário autenticado
- Extrai role do token JWT
- Valida token e contexto

---

### 4. **GET /auth/sessions**
**Protegido (requer autenticação)**
```
Method: GET
Auth Required: ✅ Sim (Bearer Token)
Headers: Authorization: Bearer <access_token>
```

**Response (200 OK):**
```json
[
  {
    "sessionId": "uuid",
    "ipAddress": "string",
    "userAgent": "string",
    "createdAt": "2026-03-19T10:30:00Z",
    "lastActivity": "2026-03-19T10:35:00Z"
  },
  ...
]
```

**Funcionalidade:**
- Lista todas as sessões ativas do usuário
- Marca sessão atual como "current"
- Inclui IP e User-Agent de cada sessão
- Exclui a sessão do token atual

---

### 5. **DELETE /auth/sessions/{sessionId}**
**Protegido (requer autenticação)**
```
Method: DELETE
Auth Required: ✅ Sim (Bearer Token)
Path Param: sessionId (uuid)
Headers: Authorization: Bearer <access_token>
```

**Response (204 No Content):**
```
(sem corpo)
```

**Funcionalidade:**
- Revoga uma sessão específica
- Invalida todos os tokens dessa sessão
- Força logout naquele dispositivo/navegador

---

### 6. **DELETE /auth/sessions**
**Protegido (requer autenticação)**
```
Method: DELETE
Auth Required: ✅ Sim (Bearer Token)
Headers: Authorization: Bearer <access_token>
```

**Response (204 No Content):**
```
(sem corpo)
```

**Funcionalidade:**
- Revoga TODAS as sessões do usuário
- Força logout em todos os dispositivos
- Invalida todos os tokens de refresh

---

### 7. **POST /auth/refresh**
**Público (sem autenticação no header)**
```
Method: POST
Content-Type: application/json
Auth Required: ❌ Não (usa refresh token no body)
```

**Request Body:**
```json
{
  "refreshToken": "string"
}
```

**Response (200 OK):**
```json
{
  "accessToken": "new_jwt_token",
  "refreshToken": "new_refresh_token"
}
```

**Funcionalidade:**
- Renova access token usando refresh token
- Valida IP e User-Agent
- Gera novo refresh token (token rotation)
- Mantém tokenId da sessão (persistência cross-refresh)
- Busca sessão ativa para coordenação
- ⚠️ **Importante:** Não requer Authorization header, usa token no body

---

### 8. **POST /auth/logout**
**Protegido (requer autenticação)**
```
Method: POST
Auth Required: ✅ Sim (Bearer Token)
Headers: Authorization: Bearer <access_token>
```

**Response (204 No Content):**
```
(sem corpo)
```

**Funcionalidade:**
- Revoga a sessão atual (por tokenId)
- Invalida o token JWT usado
- Remove sessão do banco de dados

---

### 9. **POST /auth/change-password**
**Protegido (requer autenticação)**
```
Method: POST
Content-Type: application/json
Auth Required: ✅ Sim (Bearer Token)
Headers: Authorization: Bearer <access_token>
```

**Request Body:**
```json
{
  "currentPassword": "string",
  "newPassword": "string"
}
```

**Response (204 No Content):**
```
(sem corpo)
```

**Funcionalidade:**
- Valida senha atual
- Altera password do usuário
- **Efeito colateral:** Revoga TODAS as outras sessões como medida de segurança
- Força logout em todos os dispositivos após mudança

---

### 10. **GET /auth/debug** (DEV ONLY)
**Protegido (dev profile)**
```
Method: GET
Profile: dev (ativo apenas em ambiente de desenvolvimento)
Auth Required: ⚠️ Opcional
```

**Response (200 OK):**
```json
{
  "authenticated": true|false,
  "principal": "class_name",
  "authorities": ["ROLE_VENDOR", "ROLE_USER"]
}
```

**Funcionalidade:**
- Endpoint de debug exclusivamente para desenvolvimento
- Mostra estado de autenticação
- Mostra tipo de principal e authorities
- Desabilitado em produção

---

## 🔐 Headers Comuns

### Para endpoints protegidos:
```
Authorization: Bearer <access_token>
Content-Type: application/json (quando necessário)
X-Tenant-Id: public (opcional, resolve automaticamente se em contexto)
```

### Para endpoints públicos:
```
Content-Type: application/json
X-Tenant-Id: public (opcional, padrão para /auth/register e /auth/login)
```

---

## 📊 Classificação por Tipo (IMPLEMENTADOS)

### **Endpoints Públicos (sem autenticação):**
- POST /auth/register
- POST /auth/login
- POST /auth/refresh

### **Endpoints Protegidos (requer token):**
- GET /auth/me
- GET /auth/sessions
- DELETE /auth/sessions/{sessionId}
- DELETE /auth/sessions
- POST /auth/logout
- POST /auth/change-password

### **Endpoints Dev-Only:**
- GET /auth/debug

---

## 📊 Endpoints FALTANDO por Categoria

### **Endpoints Públicos (faltando):**
- POST /auth/forgot-password (request password reset)
- POST /auth/reset-password (perform reset com token)

---

## 🔄 Fluxo Típico de Autenticação

```
1. POST /auth/register          (criar conta)
   ↓
2. Recebe access_token + refresh_token
   ↓
3. GET /auth/me                 (validar autenticação)
   ↓
4. Usa access_token (válido por ~15min)
   ↓
5. Quando expirar: POST /auth/refresh (renovar token)
   ↓
6. Recebe novo access_token
   ↓
7. Na saída: POST /auth/logout  (revoga sessão)
```

---

## 🛡️ Fluxo de Múltiplas Sessões

```
Cliente A (Mobile)
  ↓
  POST /auth/login → sessionId_1
  GET /auth/sessions → mostra sessions 1 e 2
  ↓

Cliente B (Web)
  ↓
  POST /auth/login → sessionId_2
  GET /auth/sessions → mostra sessions 1 e 2
  ↓

Revoke options:
- DELETE /auth/sessions/{sessionId_1}  (logout apenas mobile)
- DELETE /auth/sessions                 (logout tudo)
```

---

## ⚠️ Casos de Erro Comum

| Endpoint | HTTP Status | Erro Comum | Solução |
|----------|-------------|-----------|---------|
| POST /register | 400 | Email já existe | Usar outro email |
| POST /register | 400 | Password fraco | Min 8 chars, 1 maiús, 1 número |
| POST /register | 400 | Confirmação não bate | confirmPassword deve = password |
| POST /login | 401 | Credenciais inválidas | Verificar email/password |
| GET /auth/me | 401 | Token inválido/expirado | Fazer novo login ou refresh |
| POST /refresh | 401 | Refresh token inválido/expirado | Fazer novo login |
| POST /refresh | 401 | IP/User-Agent mudou | Possível tentativa maliciosa |
| POST /change-password | 401 | Current password errada | Confirmar senha atual |
| POST /logout | 204 | Sucesso silencioso | Token revogado após saida |

---

## 📝 Notas Importantes

1. **Token Rotation:** A cada `/auth/refresh`, um novo refresh token é gerado
2. **Session Persistence:** O `tokenId` persiste durante token renewal (não é criada nova sessão)
3. **Segurança:** `/auth/change-password` revoga TODAS as outras sessões
4. **Multi-tenant:** Tenants públicos usam X-Tenant-Id header, internos usam TenantContext
5. **Brute Force Protection:** Implementado em `/auth/login` (5 tentativas = bloqueio temporário)
6. **Access Token TTL:** ~15 minutos (configurável)
7. **Refresh Token TTL:** ~7 dias (configurável)

---

## 🧪 Teste Rápido

```powershell
# 1. Register
$register = Invoke-RestMethod -Uri "http://localhost:8081/auth/register" `
  -Method POST `
  -Headers @{"Content-Type"="application/json"} `
  -Body (ConvertTo-Json @{
    name="Test"
    email="test@example.com"
    password="SecurePass123!@"
    confirmPassword="SecurePass123!@"
  })

# 2. Usar token
$token = $register.accessToken
$headers = @{"Authorization"="Bearer $token"}

# 3. Get /auth/me
Invoke-RestMethod -Uri "http://localhost:8081/auth/me" `
  -Method GET `
  -Headers $headers

# 4. Logout
Invoke-RestMethod -Uri "http://localhost:8081/auth/logout" `
  -Method POST `
  -Headers $headers
```

---

## 🚀 Próximos Passos - Implementar Endpoints Faltando

### Para implementar os 2 endpoints que faltam (forgot-password e reset-password):

#### **Passo 1: Criar DTOs**

**ForgotPasswordRequest.java**
```java
@Data
public record ForgotPasswordRequest(
    @Email @NotBlank
    String email
) {}
```

**ResetPasswordRequest.java**
```java
@Data
public record ResetPasswordRequest(
    @NotBlank
    String token,
    
    @NotBlank(message = "Senha obrigatória")
    @Size(min = 8, message = "Mínimo 8 caracteres")
    @Pattern(regex = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$", 
             message = "Deve conter maiúscula, minúscula e número")
    String newPassword
) {}
```

#### **Passo 2: Injetar PasswordResetService no AuthController**

```java
private final PasswordResetService passwordResetService;

public AuthController(
    AuthService authService,
    JwtService jwtService,
    RefreshTokenService refreshTokenService,
    UserSessionService userSessionService,
    TenantService tenantService,
    PasswordResetService passwordResetService  // ← ADD
) {
    // ... initialize all fields
}
```

#### **Passo 3: Adicionar endpoints ao AuthController**

```java
/* ======================================================
   FORGOT PASSWORD
   ====================================================== */

@PostMapping("/forgot-password")
public ResponseEntity<Map<String, String>> forgotPassword(
    @Valid @RequestBody ForgotPasswordRequest request
) {
    // Anti-enumeração: sempre retorna sucesso
    passwordResetService.requestPasswordReset(request.email());
    
    return ResponseEntity.ok(Map.of(
        "message", "Se o email existe, você receberá um link para resetar a senha"
    ));
}

/* ======================================================
   RESET PASSWORD
   ====================================================== */

@PostMapping("/reset-password")
public ResponseEntity<Void> resetPassword(
    @Valid @RequestBody ResetPasswordRequest request
) {
    try {
        passwordResetService.resetPassword(request.token(), request.newPassword());
        return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
        throw new IllegalArgumentException("Token inválido ou expirado");
    }
}
```

#### **Passo 4: Atualizar SecurityConfig**

```java
.requestMatchers(
    "/auth/register",
    "/auth/login",
    "/auth/refresh",
    "/auth/forgot-password",      // ← ADD
    "/auth/reset-password"        // ← ADD
).permitAll()
```

### ⏱️ Esforço Estimado: **30 minutos**
- DTOs: 5 min
- Endpoints: 15 min
- Testes: 10 min

---

## 📌 Resumo Final

| Métrica | Valor |
|---------|-------|
| **Total de Endpoints** | 12 (10 implementados + 2 planejados) |
| **Implementados** | 10 ✅ |
| **Faltando** | 2 (com infraestrutura pronta) ⚠️ |
| **Infraestrutura** | 100% pronta 🔧 |
| **Segurança** | ⭐⭐⭐⭐⭐ |
| **Multi-tenant** | ✅ Suportado |
| **Rate Limiting** | ✅ Implementado (20 req/min) |
| **Testes** | ✅ test-auth-only.ps1 disponível |
