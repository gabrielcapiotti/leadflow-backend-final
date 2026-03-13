# 📋 Credenciais de Teste - LeadFlow Backend

## Usuários de Teste Padrão

Todas as requisições devem incluir o header:
```
X-Tenant-ID: public
```

---

## ✅ Usuários Registrados (Pronto para Login)

### Carlos Silva
```properties
Email: carlos@leadflow.com
Password: SenhaForte@123
Tenant: public
```

**PowerShell Command:**
```powershell
$h = @{"X-Tenant-ID"="public"}
$b = @{email="carlos@leadflow.com";password="SenhaForte@123"} | ConvertTo-Json
$r = Invoke-RestMethod "http://localhost:8081/auth/login" -Method POST -Headers $h -ContentType "application/json" -Body $b
$r
```

### Ana Costa
```properties
Email: ana@leadflow.com
Password: SenhaForte@123
Tenant: public
```

**PowerShell Command:**
```powershell
$h = @{"X-Tenant-ID"="public"}
$b = @{email="ana@leadflow.com";password="SenhaForte@123"} | ConvertTo-Json
$r = Invoke-RestMethod "http://localhost:8081/auth/login" -Method POST -Headers $h -ContentType "application/json" -Body $b
$r
```

### Pedro Santos
```properties
Email: pedro@leadflow.com
Password: Senha@123456
Tenant: public
```

**PowerShell Command:**
```powershell
$h = @{"X-Tenant-ID"="public"}
$b = @{email="pedro@leadflow.com";password="Senha@123456"} | ConvertTo-Json
$r = Invoke-RestMethod "http://localhost:8081/auth/login" -Method POST -Headers $h -ContentType "application/json" -Body $b
$r
```

---

## 📝 Registrar Novo Usuário

**Endpoint:** `POST /auth/register`

```powershell
$headers = @{"X-Tenant-ID"="public"}
$body = @{
    name = "Nome Completo"
    email = "email@example.com"
    password = "Senha@123456"
    confirmPassword = "Senha@123456"
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "http://localhost:8081/auth/register" `
    -Method POST `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $body

$response | ConvertTo-Json
```

---

## 🔐 Login - Obter JWT Tokens

**Endpoint:** `POST /auth/login`

```powershell
$headers = @{"X-Tenant-ID"="public"}
$body = @{
    email = "carlos@leadflow.com"
    password = "SenhaForte@123"
} | ConvertTo-Json

$response = Invoke-RestMethod -Uri "http://localhost:8081/auth/login" `
    -Method POST `
    -Headers $headers `
    -ContentType "application/json" `
    -Body $body

# Acessar os tokens
$accessToken = $response.accessToken
$refreshToken = $response.refreshToken

Write-Host "Access Token: $($accessToken.Substring(0, 50))..."
Write-Host "Refresh Token: $($refreshToken.Substring(0, 50))..."
```

---

## 🚀 Usar JWT Token em Requisições Autenticadas

```powershell
# Depois de fazer login e obter o accessToken
$accessToken = "seu_token_aqui"

$headers = @{
    "X-Tenant-ID" = "public"
    "Authorization" = "Bearer $accessToken"
}

# Exemplo: Chamar endpoint autenticado
$response = Invoke-RestMethod -Uri "http://localhost:8081/api/leads" `
    -Method GET `
    -Headers $headers

$response | ConvertTo-Json
```

---

## 🧪 Script Rápido para Teste Completo

Salve como `test_auth.ps1`:

```powershell
# === CONFIGURAÇÕES ===
$TENANT = "public"
$EMAIL = "carlos@leadflow.com"
$PASSWORD = "SenhaForte@123"
$BASE_URL = "http://localhost:8081"

# === HEADERS ===
$headers = @{
    "X-Tenant-ID" = $TENANT
    "Content-Type" = "application/json"
}

# === LOGIN ===
Write-Host "🔐 Fazendo login..." -ForegroundColor Cyan
$loginBody = @{
    email = $EMAIL
    password = $PASSWORD
} | ConvertTo-Json

try {
    $loginResponse = Invoke-RestMethod -Uri "$BASE_URL/auth/login" `
        -Method POST `
        -Headers $headers `
        -Body $loginBody
    
    $accessToken = $loginResponse.accessToken
    $refreshToken = $loginResponse.refreshToken
    
    Write-Host "✅ Login realizado com sucesso!" -ForegroundColor Green
    Write-Host "📌 Access Token: $($accessToken.Substring(0, 40))..." -ForegroundColor Yellow
    Write-Host "📌 Refresh Token: $($refreshToken.Substring(0, 40))..." -ForegroundColor Yellow
    
    # Salvar token para próximas requisições
    $authHeaders = @{
        "X-Tenant-ID" = $TENANT
        "Authorization" = "Bearer $accessToken"
        "Content-Type" = "application/json"
    }
    
    # === TESTE: Chamar endpoint autenticado ===
    Write-Host "`n📋 Consultando leads..." -ForegroundColor Cyan
    try {
        $leadsResponse = Invoke-RestMethod -Uri "$BASE_URL/api/leads" `
            -Method GET `
            -Headers $authHeaders
        Write-Host "✅ Leads obtidos com sucesso!" -ForegroundColor Green
        $leadsResponse | ConvertTo-Json
    } catch {
        Write-Host "❌ Erro ao obter leads: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    }
    
} catch {
    Write-Host "❌ Erro ao fazer login: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    Write-Host "Mensagem: $($_.Exception.Message)" -ForegroundColor Red
}
```

**Executar:**
```powershell
cd "C:\Users\Gabri\OneDrive\Área de Trabalho\leadflow-backend\leadflow-backend"
powershell.exe -ExecutionPolicy Bypass -File test_auth.ps1
```

---

## 📊 Resumo das Credenciais

| Nome | Email | Password | Tenant | Status |
|------|-------|----------|--------|--------|
| Carlos Silva | carlos@leadflow.com | SenhaForte@123 | public | ✅ Ativo |
| Ana Costa | ana@leadflow.com | SenhaForte@123 | public | ✅ Ativo |
| Pedro Santos | pedro@leadflow.com | Senha@123456 | public | ✅ Ativo |

---

## ⚙️ Configurações do Servidor

```properties
Server: http://localhost:8081
Database: PostgreSQL (localhost:2411/leadflow_test)
Tenant Padrão: public
Timeout: 10 segundos para requisições
```

---

## 🔄 Fluxo Completo: Register → Login → Requisição Autenticada

```powershell
# 1️⃣ REGISTRAR
$registerBody = @{
    name = "João Silva"
    email = "joao@example.com"
    password = "Senha@123456"
    confirmPassword = "Senha@123456"
} | ConvertTo-Json

$h = @{"X-Tenant-ID"="public"}
$register = Invoke-RestMethod "http://localhost:8081/auth/register" -Method POST -Headers $h -ContentType "application/json" -Body $registerBody

# 2️⃣ LOGIN (usar email e password do registro)
$loginBody = @{
    email = "joao@example.com"
    password = "Senha@123456"
} | ConvertTo-Json

$login = Invoke-RestMethod "http://localhost:8081/auth/login" -Method POST -Headers $h -ContentType "application/json" -Body $loginBody
$accessToken = $login.accessToken

# 3️⃣ USAR TOKEN EM REQUISIÇÃO AUTENTICADA
$authHeaders = @{
    "X-Tenant-ID" = "public"
    "Authorization" = "Bearer $accessToken"
}

$result = Invoke-RestMethod "http://localhost:8081/api/leads" -Method GET -Headers $authHeaders
$result
```

---

## ❌ Erros Comuns

### 401 Unauthorized (Login)
- ✗ Email ou password incorretos
- ✗ Usuário não existe
- ✗ Header `X-Tenant-ID` faltando
- ✓ **Solução:** Verify credenciais com tabela acima

### 400 Bad Request (Register)
- ✗ Password não atende requisitos (min 8 caracteres)
- ✗ Email inválido
- ✗ confirmPassword diferente de password
- ✓ **Solução:** Usar `SenhaForte@123` ou similar

### 503 Service Unavailable
- ✗ Servidor não está rodando
- ✓ **Solução:** `mvn spring-boot:run -DskipTests`

---

## 💡 Dicas

1. **Sempre incluir header:** `X-Tenant-ID: public`
2. **Testar login primeiro** antes de requisições autenticadas
3. **Copiar accessToken** após login para requisições
4. **Token expira em:** 1 hora (verifique `jwtService`)
5. **Usar refreshToken** para obter novo accessToken

---

**Última atualização:** 13/03/2026
**Status:** ✅ Produção
