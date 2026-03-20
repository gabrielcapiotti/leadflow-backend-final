# 🔐 Endpoints de Auth FALTANDO

## Resumo
**2 endpoints de password recovery** precisam ser implementados. Toda a infraestrutura (service, banco de dados, email) está pronta.

---

## 📋 Endpoints a Implementar

### 1. POST /auth/forgot-password
Solicita redefinição de senha enviando link por email

**Status: ⚠️ FALTANDO (Service 100% pronto)**

**Request:**
```json
{
  "email": "user@example.com"
}
```

**Response (200 OK - always):**
```json
{
  "message": "Se o email existe, você receberá um link para resetar a senha"
}
```

**Infraestrutura Existente:**
- ✅ `PasswordResetService.requestPasswordReset(email)` - gera token seguro
- ✅ Tabela `password_reset_token` - armazena tokens com hash
- ✅ `SendGridEmailService` - envia emails
- ✅ Validação de expiração (15 minutos)
- ✅ Anti-enumeração implementada

---

### 2. POST /auth/reset-password
Redefinição de senha usando token do email

**Status: ⚠️ FALTANDO (Service 100% pronto)**

**Request:**
```json
{
  "token": "token_received_in_email",
  "newPassword": "NewSecurePass123!@"
}
```

**Response (204 No Content):**
```
(sem corpo - sucesso)
```

**Infraestrutura Existente:**
- ✅ `PasswordResetService.resetPassword(token, password)` - valida e atualiza
- ✅ Hash seguro com BCrypt
- ✅ Validação de token expirado
- ✅ Validação de token já utilizado
- ✅ Revogação de todas as sessões do usuário (segurança)
- ✅ Limpeza de tokens antigos

---

## ✅ Checklist de Implementação

### 1. Criar DTOs (5 minutos)
- [ ] Criar `ForgotPasswordRequest.java`
  ```java
  public record ForgotPasswordRequest(
      @Email @NotBlank String email
  ) {}
  ```

- [ ] Criar `ResetPasswordRequest.java`
  ```java
  public record ResetPasswordRequest(
      @NotBlank String token,
      @NotBlank @Size(min=8) @Pattern(...) String newPassword
  ) {}
  ```

**Localização:** `src/main/java/com/leadflow/backend/dto/auth/`

---

### 2. Injetar PasswordResetService (5 minutos)
- [ ] Importar `com.leadflow.domain.auth.service.PasswordResetService`
- [ ] Adicionar ao construtor do `AuthController`
- [ ] Inicializar field privado

**Arquivo:** `src/main/java/com/leadflow/backend/controller/auth/AuthController.java`

---

### 3. Adicionar Endpoints (15 minutos)
- [ ] Implementar `@PostMapping("/forgot-password")`
  - Valida request
  - Chama `passwordResetService.requestPasswordReset(email)`
  - Retorna 200 sempre (anti-enumeração)

- [ ] Implementar `@PostMapping("/reset-password")`
  - Valida request
  - Chama `passwordResetService.resetPassword(token, password)`
  - Retorna 204 ou 400 (token inválido/expirado)

**Arquivo:** `src/main/java/com/leadflow/backend/controller/auth/AuthController.java`

---

### 4. Atualizar SecurityConfig (2 minutos)
- [ ] Adicionar `/auth/forgot-password` e `/auth/reset-password` à lista de `permitAll()`

**Arquivo:** `src/main/java/com/leadflow/backend/config/security/SecurityConfig.java`

---

### 5. Testes (10 minutos)
- [ ] Testar POST /auth/forgot-password com email válido
- [ ] Testar POST /auth/forgot-password com email inválido (deve retornar 200)
- [ ] Testar POST /auth/reset-password com token válido
- [ ] Testar POST /auth/reset-password com token expirado
- [ ] Verificar se email foi recebido (SendGrid)
- [ ] Verificar se novo password funciona no /auth/login

---

## 📊 Impacto

| Aspecto | Detalhes |
|---------|----------|
| **Linhas de código** | ~80 linhas (DTOs + endpoints) |
| **Novas classes** | 2 (ForgotPasswordRequest, ResetPasswordRequest) |
| **Modificações existentes** | 1 (AuthController - adicionar service e endpoints) |
| **Testes necessários** | 6 testes (happy path + edge cases) |
| **Tempo total** | 30-40 minutos para dev 1 pessoa |
| **Risco** | ✅ BAIXO - service já validado |

---

## 🔌 Integração com Frontend

### Email (SendGrid)
```
De: noreply@leadflow.com
Assunto: Redefinir sua senha
Corpo: Link para https://app.leadflow.com/reset-password?token=XXX
```

### Fluxo Completo no Frontend
```
1. Usuário clica "Esqueci a Senha"
2. Insere email → POST /auth/forgot-password
3. Recebe confirmação "Verifique seu email"
4. Clica no link do email → reset.html?token=ABC123
5. Insere nova senha → POST /auth/reset-password {token, newPassword}
6. Sucesso → redireciona para /login
```

---

## 🔒 Segurança Implementada

✅ **No Forgot-Password:**
- Anti-enumeração (não revela se email existe)
- Token seguro de 32 bytes com SHA-256
- Expiração de 15 minutos
- Tokens antigos invalidados
- Limitado a tentativas por IP (rate limit)

✅ **No Reset-Password:**
- Validação de token hash (SHA-256)
- Verificação de expiração
- Verificação de uso (token one-time)
- Password com hash BCrypt
- Revoga TODAS as sessões (força logout universal)
- Log de auditoria

---

## 📝 Exemplo de Código Completo

```java
/* ======================================================
   FORGOT PASSWORD
   ====================================================== */

@PostMapping("/forgot-password")
public ResponseEntity<Map<String, String>> forgotPassword(
    @Valid @RequestBody ForgotPasswordRequest request
) {
    log.info("Password reset requested for: {}", maskEmail(request.email()));
    
    // Always returns 200 (anti-enumeration)
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
        log.info("Password reset attempted");
        passwordResetService.resetPassword(request.token(), request.newPassword());
        log.info("Password reset successful");
        return ResponseEntity.noContent().build();
    } catch (IllegalArgumentException e) {
        log.warn("Password reset failed: {}", e.getMessage());
        throw new BadCredentialsException("Token inválido ou expirado");
    }
}
```

---

## 📞 Suporte

**Dúvidas sobre:**
- PasswordResetService: Ver `com.leadflow.domain.auth.service.PasswordResetService`
- DTOs existentes: Ver `src/main/java/com/leadflow/backend/dto/auth/`
- SendGrid: Ver `com.leadflow.backend.service.notification.SendGridEmailService`
- SecurityConfig: Ver `src/main/java/com/leadflow/backend/config/security/`

