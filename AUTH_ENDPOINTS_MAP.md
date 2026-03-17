# 📋 Mapeamento Completo de Endpoints de Autenticação

## Configuração Base
- **Base URL**: `http://localhost:8081/auth`
- **Context Path**: `/api` (configurado em application-dev.yml e application-prod.yml)
- **Header Obrigatório**: `X-Tenant-ID: public` (para testes)
- **Taxa de Limite**: 20 requisições/minuto para escopo "auth"

---

## 1️⃣ REGISTER - POST `/auth/register`

### Descrição
Registra um novo usuário e retorna tokens JWT

### Segurança
- ✅ Público (sem autenticação)
- ✅ Validação de DTO obrigatória

### Payload Obrigatório (RegisterRequest)
```json
{
  "name": "João Silva",           // String, @NotBlank, min=3, max=100
  "email": "joao@example.com",    // String, @Email, @NotBlank
  "password": "Senha@12345",      // String, @NotBlank, min=8
  "confirmPassword": "Senha@12345" // String, @NotBlank, deve ser igual a password
}
```

### Resposta Sucesso (201 Created)
```json
{
  "accessToken": "eyJhbGc...",  // JWT token válido
  "refreshToken": "hash-base64..." // Token de refresh
}
```

### Respostas de Erro
| Código | Situação |
|--------|----------|
| 400 | Email já registrado / Email inválido / Nome muito curto / Senha < 8 chars / Confirmação diferente |
| 429 | Rate limit excedido (20 reqs/min) |

### Fluxo Interno
1. `authService.registerUser()` - Valida email único, hash de senha, cria User
2. `jwtService.generateToken()` - Gera JWT access token
3. `userSessionService.createSession()` - Cria sessão com IP e User-Agent
4. `refreshTokenService.generate()` - Gera refresh token com hash e validade (7 dias)

### Validações Aplicadas
- ✅ Email único (único por tenant)
- ✅ Senha >= 8 caracteres
- ✅ Nome entre 3-100 caracteres
- ✅ Confirmação de senha obrigatória e deve coincidir

---

## 2️⃣ LOGIN - POST `/auth/login`

### Descrição
Autentica usuário existente e retorna tokens JWT

### Segurança
- ✅ Público (sem autenticação)
- ✅ Proteção contra brute force (5 tentativas / 5 minutos)

### Payload Obrigatório (LoginRequest)
```json
{
  "email": "joao@example.com",  // String, @Email, @NotBlank
  "password": "Senha@12345"     // String, @NotBlank, min=6 (nota: diferente do register!)
}
```

### Resposta Sucesso (200 OK)
```json
{
  "accessToken": "eyJhbGc...",
  "refreshToken": "hash-base64..."
}
```

### Respostas de Erro
| Código | Situação |
|--------|----------|
| 400 | Validação DTO falhou / Credenciais inválidas (PROBLEMA: deveria ser 401) |
| 401 | Conta bloqueada por brute force |
| 429 | Rate limit excedido |

### Fluxo Interno
1. `authService.authenticateUser()` - Hash de senha, compara com BD
2. `bruteForceService` - Verifica tentativas falhas anteriores
3. `jwtService.generateToken()` - Gera novo JWT
4. `userSessionService.createSession()` - Novo registro de sessão
5. `refreshTokenService.generate()` - Novo refresh token

### ⚠️ PROBLEMA IDENTIFICADO
- **Esperado**: HTTP 401 para credenciais inválidas
- **Atual**: HTTP 400 (Bad Request)
- **Causa**: Validação DTO retorna 400 antes de autenticação

---

## 3️⃣ GET `/auth/me` - GET (Autenticado)

### Descrição
Retorna informações do usuário atualmente autenticado

### Segurança
- 🔐 Requer JWT Token no header `Authorization: Bearer <token>`
- 🔐 Validação de sessão ativa

### Headers Obrigatórios
```
Authorization: Bearer eyJhbGc...
X-Tenant-ID: public
```

### Resposta Sucesso (200 OK)
```json
{
  "id": "uuid-12345",
  "email": "joao@example.com",
  "role": "ROLE_USER"
}
```

### Respostas de Erro
| Código | Situação |
|--------|----------|
| 401 | Sem token / Token inválido / Token expirado |
| 403 | Permissão insuficiente |

### Fluxo Interno
1. `JwtAuthenticationFilter` - Extrai e valida JWT
2. `TenantFilter` - Resolve tenant do token
3. `requireAuthenticatedUser()` - Verifica `Authentication` não nula
4. Retorna dados do `CustomUserDetails`

---

## 4️⃣ SESSIONS - GET `/auth/sessions` (Autenticado)

### Descrição
Lista todas as sessões ativas do usuário (multi-device)

### Segurança
- 🔐 Requer autenticação JWT
- 📍 Extrai IP e User-Agent da requisição

### Headers Obrigatórios
```
Authorization: Bearer eyJhbGc...
X-Tenant-ID: public
```

### Resposta Sucesso (200 OK)
```json
[
  {
    "sessionId": "uuid-session-1",
    "ipAddress": "192.168.1.100",
    "userAgent": "Mozilla/5.0...",
    "createdAt": "2026-03-16T10:00:00Z",
    "current": true  // Marca se é a sessão atual
  },
  {
    "sessionId": "uuid-session-2",
    "ipAddress": "192.168.1.101",
    "userAgent": "Mozilla/5.0...",
    "createdAt": "2026-03-15T15:00:00Z",
    "current": false
  }
]
```

### Respostas de Erro
| Código | Situação |
|--------|----------|
| 401 | Sem autenticação |
| 404 | Usuário não encontrado |

### Fluxo Interno
1. `userSessionService.listActiveSessions()` - Query all sessions for user
2. Marca a sessão atual baseado no `tokenId` extraído
3. Retorna lista de `SessionResponse`

---

## 5️⃣ DELETE `/auth/sessions` (Autenticado)

### Descrição
Revoga TODAS as sessões do usuário em todos os dispositivos

### Segurança
- 🔐 Requer autenticação
- ⚠️ Força logout imediato em todos os dispositivos

### Headers Obrigatórios
```
Authorization: Bearer eyJhbGc...
```

### Resposta Sucesso (204 No Content)
```
(vazio)
```

### Respostas de Erro
| Código | Situação |
|--------|----------|
| 401 | Sem autenticação |

### Fluxo Interno
1. `userSessionService.revokeAllUserSessions(userId, tenantId)` - Marca todas como revogadas

---

## 6️⃣ DELETE `/auth/sessions/{sessionId}` (Autenticado)

### Descrição
Revoga uma sessão específica (logout de um dispositivo)

### Segurança
- 🔐 Requer autenticação
- ✅ Valida propriedade (usuário pode deletar apenas suas sessões)

### Headers Obrigatórios
```
Authorization: Bearer eyJhbGc...
```

### Parâmetros
- `sessionId` (Path): UUID da sessão a revogar

### Resposta Sucesso (204 No Content)
```
(vazio)
```

### Respostas de Erro
| Código | Situação |
|--------|----------|
| 401 | Sem autenticação |
| 404 | Sessão não encontrada |
| 403 | Tentando revogar sessão de outro usuário |

### Fluxo Interno
1. `userSessionService.revokeSpecificSession(sessionId, userId, tenantId, null)`

---

## 7️⃣ POST `/auth/refresh` (Sem autenticação, com refresh token)

### Descrição
Renova o access token usando o refresh token (token rotation segura)

### Segurança
- ✅ Não requer JWT no header
- ✅ Valida IP e User-Agent (device binding)
- ⚠️ **PROBLEMA**: Retorna 500 em vez de processar corretamente

### Headers Obrigatórios
```
X-Tenant-ID: public
```

### Payload Obrigatório (RefreshTokenRequest)
```json
{
  "refreshToken": "hash-base64..."  // String, @NotBlank
}
```

### Resposta Esperada (200 OK)
```json
{
  "accessToken": "eyJhbGc...",      // Novo JWT
  "refreshToken": "novo-hash..."    // Novo refresh token (token rotation)
}
```

### Possíveis Respostas de Erro
| Código | Situação |
|--------|----------|
| 400 | Payload inválido / Refresh token inválido ou vazio |
| 401 | Refresh token expirado / Device mismatch / Token reuse detectado |
| 500 | **PROBLEMA IDENTIFICADO** - Erro interno durante renovação |
| 429 | Rate limit |

### Fluxo Interno
1. `refreshTokenService.validateAndRotate()`:
   - Hash do token fornecido
   - Busca no BD por `findByTokenHash(hash)`
   - ✅ Valida se revogado (reuse detection)
   - ✅ Valida se expirado
   - ✅ Valida device fingerprint (IP + User-Agent)
   - ✅ Revoga token antigo
   - ✅ Gera novo token
2. `jwtService.generateToken()` - Novo JWT
3. Retorna ambos os tokens

### ⚠️ PROBLEMA IDENTIFICADO
- **Atual**: POST `/auth/refresh` retorna HTTP 500
- **Esperado**: HTTP 200 com novos tokens
- **Suspeita**: Erro ao chamar `result.newRefreshToken()` ou na geração do JWT

---

## 8️⃣ POST `/auth/logout` (Autenticado)

### Descrição
Revoga a sessão atual do usuário

### Segurança
- 🔐 Requer autenticação
- ✅ Extrai token ID do header Authorization

### Headers Obrigatórios
```
Authorization: Bearer eyJhbGc...
X-Tenant-ID: public
```

### Resposta Sucesso (204 No Content)
```
(vazio)
```

### Respostas de Erro
| Código | Situação |
|--------|----------|
| 401 | Sem autenticação |
| 400 | Token ID não encontrado |

### Fluxo Interno
1. `extractTokenId(request)` - Extrai ID do JWT do header
2. `userSessionService.revokeSession(tokenId, tenantId)` - Marca sessão como revogada
3. Retorna 204

### ⚠️ PROBLEMA IDENTIFICADO
- **Esperado**: Após logout, o token não deveria mais funcionar
- **Atual**: Teste mostra que token ainda funciona em `/auth/me` após logout
- **Causa**: `JwtAuthenticationFilter` valida apenas JWT, não consulta status de sessão

---

## 9️⃣ POST `/auth/change-password` (Autenticado)

### Descrição
Altera senha do usuário e revoga todas as sessões (force logout)

### Segurança
- 🔐 Requer autenticação
- ✅ Valida senha atual antes de permitir mudança
- ✅ Força logout em todos os dispositivos após mudança

### Headers Obrigatórios
```
Authorization: Bearer eyJhbGc...
X-Tenant-ID: public
```

### Payload Obrigatório (ChangePasswordRequest)
```json
{
  "currentPassword": "SenhaAtual@123",   // String, @NotBlank
  "newPassword": "NovaSenha@456",        // String, @NotBlank, min=8
  "confirmPassword": "NovaSenha@456"     // String, @NotBlank, deve coincidir
}
```

### Resposta Sucesso (204 No Content)
```
(vazio)
```

### Respostas de Erro (PROBLEMA IDENTIFICADO)
| Código | Situação |
|--------|----------|
| 400 | Validação DTO falhou |
| 401 | **Esperado para senha atual inválida, mas retorna 401 mesmo com token válido** |
| 403 | Sem permissão |

### Fluxo Interno
1. `requireAuthenticatedUser()` - Valida autenticação
2. `authService.validatePassword()` - Compara senha atual
3. `authService.changePassword()` - Hash e salva nova senha
4. `userSessionService.revokeAllUserSessions()` - Logout forçado
5. Retorna 204

### ⚠️ PROBLEMA IDENTIFICADO
- **Teste mostra**: Retorna 401 mesmo com token JWT válido
- **Esperado**: Deveria processar normalmente
- **Causa**: Possível validação errada na senha atual ou erro no `authService.validatePassword()`

---

## 🔟 GET `/auth/debug` (Apenas DEV, Público)

### Descrição
Endpoint de debug para verificar estado de autenticação (apenas em profile "dev")

### Segurança
- ✅ Público (sem autenticação)
- ✅ Profile dev apenas
- 📍 Não deve estar em produção

### Headers
```
X-Tenant-ID: public
```

### Resposta sem Autenticação (200 OK)
```json
{
  "authenticated": false
}
```

### Resposta com Autenticação (200 OK)
```json
{
  "authenticated": true,
  "principal": "com.leadflow.backend.security.CustomUserDetails",
  "authorities": ["ROLE_USER"]
}
```

---

## 📊 Matriz de Problemas Identificados

| # | Endpoint | Status | Problema | Esperado | Atual |
|----|----------|--------|----------|----------|-------|
| 1 | LOGIN | ❌ | Status code inválido | 401 | 400 |
| 2 | REFRESH | ❌ | HTTP 500 Error | 200 + tokens | 500 |
| 3 | CHANGE-PASSWORD | ❌ | Retorna 401 | 204 | 401 |
| 4 | LOGOUT | ⚠️ | Token não revogado | Token inválido | Token funciona |
| 5 | REGISTER | ✅ | OK | 201 | 201 ✓ |
| 6 | GET /me | ✅ | OK | 200 | 200 ✓ |
| 7 | SESSIONS | ✅ | OK | 200 + list | 200 ✓ |
| 8 | DELETE /sessions | ✅ | OK | 204 | 204 ✓ |
| 9 | DELETE /sessions/{id} | ✅ | OK | 204 | 204 ✓ |
| 10 | DEBUG | ✅ | OK | 200 | 200 ✓ |

---

## 🔄 Fluxo Completo de Autenticação

```
1. REGISTER (/auth/register)
   ├─ Cria User + Role
   ├─ Gera Access Token (JWT)
   ├─ Gera Refresh Token (hash + device binding)
   └─ Cria Session (IP + User-Agent)

2. LOGIN (/auth/login)
   ├─ Valida credenciais
   ├─ Verifica brute force
   ├─ Gera novo Access Token
   ├─ Gera novo Refresh Token
   └─ Cria nova Session

3. USE TOKEN (/auth/me, /auth/sessions, etc)
   ├─ JwtAuthenticationFilter extrai token
   ├─ Valida assinatura JWT
   ├─ TenantFilter define contexto
   ├─ RateLimitFilter valida limite
   └─ Controller processa requisição

4. REFRESH (/auth/refresh)
   ├─ Hash do refresh token
   ├─ Valida device fingerprint
   ├─ Detecta token reuse
   ├─ Revoga token antigo
   ├─ Gera novos tokens (rotation)
   └─ Retorna novos Access + Refresh

5. LOGOUT (/auth/logout)
   ├─ Extrai Token ID do JWT
   ├─ Marca session como revogada
   └─ Usuário vira 401 na próxima requisição

6. CHANGE PASSWORD (/auth/change-password)
   ├─ Valida senha atual
   ├─ Atualiza senha
   ├─ Revoga TODAS as sessions
   └─ Força logout em todos os dispositivos
```

---

## 🛠️ Dependências de Serviço

### Serviços Principais
- `AuthService` - Lógica de register, login, validatePassword, changePassword
- `JwtService` - Geração e validação de JWT
- `RefreshTokenService` - Geração, validação e rotation de refresh tokens
- `UserSessionService` - CRUD de sessões, validação
- `TenantService` - Resolução de tenant ID
- `BruteForceProtectionService` - Proteção contra brute force

### Filtros de Segurança (Ordem Crítica)
1. `TenantFilter` - Extrai X-Tenant-ID header
2. `JwtAuthenticationFilter` - Valida JWT e popula Authentication
3. `RateLimitFilter` - Aplica rate limiting por escopo

### Validadores
- `RegisterRequest` - @NotBlank, @Email, @Size
- `LoginRequest` - @Email, @NotBlank
- `RefreshTokenRequest` - @NotBlank
- `ChangePasswordRequest` - @NotBlank, @Size

---

## 📝 Preparação para Scripts de Teste

### Headers Padrão
```bash
-H "Content-Type: application/json"
-H "X-Tenant-ID: public"
-H "User-Agent: Test-Script/1.0"
```

### Endpoints Públicos (sem JWT)
-  POST `/auth/register`
- POST `/auth/login`
- POST `/auth/refresh`
- GET `/auth/debug` (dev only)

### Endpoints Autenticados (requerem JWT)
- GET `/auth/me`
- GET `/auth/sessions`
- DELETE `/auth/sessions`
- DELETE `/auth/sessions/{sessionId}`
- POST `/auth/logout`
- POST `/auth/change-password`

### Padrão de Token
```
Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### Base URL para Testes
```bash
http://localhost:8081/auth
```
