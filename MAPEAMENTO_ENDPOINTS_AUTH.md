# ANÁLISE: Endpoints de Autenticação

## ❌ PADRÃO INCORRETO (Retorna 401)
- POST `/api/auth/register` → 401 Unauthorized
- POST `/api/auth/login` → 401 Unauthorized

**Scripts com erro:**
- test-vendor-complete-flow.ps1
- test-vendor-with-login.ps1

---

## ✅ PADRÃO CORRETO (Funciona)
- POST `/auth/register` → 201/200 OK
- POST `/auth/login` → 200 OK

**Scripts que funcionam:**
- run-endpoints-test.ps1
- test-vendors-Oficial.ps1
- test-users-management-ADMIN.ps1
- test-debug-token.ps1
- debug-401-final.ps1
- test-iso-simple.ps1
- test-seq-debug.ps1

---

## 📊 Comparação de Padrões

### ✅ WORKING - test-vendors-Oficial.ps1 (linha 126)
```powershell
$response = Invoke-WebRequest -Uri "$BaseURL/auth/register" `
    -Method POST `
    -Headers @{"Content-Type" = "application/json"} `
    -Body ($registerBody | ConvertTo-Json)
```

### ✅ WORKING - test-vendors-Oficial.ps1 (linha 169)
```powershell
$loginResponse = Invoke-WebRequest -Uri "$BaseURL/auth/login" `
    -Method POST `
    -Headers @{"Content-Type" = "application/json"} `
    -Body ($loginBody | ConvertTo-Json)
```

### ❌ NOT WORKING - test-vendor-complete-flow.ps1 (linha 19)
```powershell
$regResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/register" `  # ← ERRADO
    -Method POST `
    -Headers $headers `
    -Body (@{ ... } | ConvertTo-Json)
```

### ❌ NOT WORKING - test-vendor-complete-flow.ps1 (linha 39)
```powershell
$loginResp = Invoke-RestMethod -Uri "$baseUrl/api/auth/login" `  # ← ERRADO
    -Method POST `
    -Headers $headers `
    -Body (@{ ... } | ConvertTo-Json)
```

---

## 🔧 FIX: Onde remover `/api`

**Em test-vendor-complete-flow.ps1:**
- Linha 19: `$baseUrl/api/auth/register` → `$baseUrl/auth/register`
- Linha 39: `$baseUrl/api/auth/login` → `$baseUrl/auth/login`

**Em test-vendor-with-login.ps1:**
- Linha 35: `$baseUrl/api/auth/login` → `$baseUrl/auth/login`
- (Linha 15 já está correto: `$baseUrl/auth/register`)

---

## 📝 Regra Geral

| Endpoint | Correto | Errado |
|----------|---------|--------|
| Register | `/auth/register` | `/api/auth/register` ❌ |
| Login | `/auth/login` | `/api/auth/login` ❌ |
| Leads | `/api/leads` ✅ | `/leads` ❌ |
| Metrics | `/api/vendor-leads/metrics` ✅ | `/vendor-leads/metrics` ❌ |

**Padrão:** Endpoints de auth NÃO têm `/api`, endpoints de negócio TÊM `/api`
