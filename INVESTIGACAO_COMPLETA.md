# INVESTIGAÇÃO COMPLETA - Problemas do Test-Admin

## Problema 1: Login Falha com HTTP 401 ✅ SOLUÇÃO ENCONTRADA

### Causa Raiz:
**Login REQUER X-Tenant-ID no header!**

Código AuthController.java (linha 177):
```java
String tenant = extractTenantFromRequest(httpRequest);

if (tenant == null || tenant.isBlank()) {
    log.error("❌ Login failed: No tenant provided in request");
    throw new UnauthorizedException("Tenant ID is required for login");
}
```

### Padrão Correto (test-billing_SUCESS.ps1, linha 205):
```powershell
$loginHeaders = @{
    "Content-Type" = "application/json"
    "X-Tenant-ID" = $global:TenantId  # ← NECESSÁRIO!
}
```

### Nosso Erro (test-admin-tranche3.ps1):
```powershell
$loginHeaders = @{
    "Content-Type" = "application/json"
    # ❌ Faltava X-Tenant-ID!
}
```

### FIX:
✅ Adicionar `"X-Tenant-ID" = $adminTenantId` ao header do login

---

## Problema 2: HTTP 400 no Segundo Registro ⚠️ 

### Análise de Padrões:

**test-billing_SUCESS.ps1:**
- Usa emails hardcoded: `teste@e2e.com`
- Expected behavior: Se existe, retorna HTTP 409 (Conflict)

**Test-Auth-Fixed_SUCESS.ps1:**  
- Gera emails ÚNICOS:
  ```powershell
  $uuid = [guid]::NewGuid().ToString().Substring(0, 8)
  $timestamp = Get-Date -Format "yyyyMMddHHmmss"
  $random1 = Get-Random -Maximum 99
  $random2 = Get-Random -Maximum 99
  $testEmail = "test-$uuid-$timestamp-$random1$random2@leadflow.dev"
  ```

### Possíveis Causas do HTTP 400:
1. **Validação de email** - `@Email` constraint falha
2. **Validação de senha** - `@Size(min = 8)` com `AdminPass123!` que tem:
   - `AdminPass123!` = 13 caracteres ✓ (> 8)
   - Mas `TestUser@123` = 12 caracteres ✓ também
3. **Validação de nome** - `@Size(min = 3, max = 100)` 
4. **Ordem de campos** - JSON pode estar mal formado

### Solução Recomendada:
✅ Usar emails ÚNICOS como Test-Auth-Fixed_SUCESS para evitar conflitos

---

## Resumo das Correções Necessárias:

| Problema | Linha | Fix | Status |
|----------|-------|-----|--------|
| Login sem X-Tenant-ID | test-admin (TEST 3) | Adicionar header | ✅ Pronto |
| Segundo registro fails | test-admin (TEST 8) | Usar emails únicos ou tratar 400/409 | ✅ Pronto |
| confirmPassword validação | AUTH | Verificar se ambos são enviados | ⚠️ Menor |

## Próximos Passos:

1. ✅ Adicionar X-Tenant-ID ao login
2. ✅ Gerar emails únicos para testes
3. ⚠️ Adicionar tratamento de erro 400 + 409 no segundo registro
