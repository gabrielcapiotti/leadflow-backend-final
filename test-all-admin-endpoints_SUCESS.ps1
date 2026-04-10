Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "    TESTANDO ENDPOINTS DO ADMIN" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8081/api"

# Step 0: Register new test user
Write-Host "[STEP 0] Registrando novo usuário de teste..." -ForegroundColor Yellow

$uuid = [guid]::NewGuid().ToString().Substring(0, 8)
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$adminEmail = "test-admin-$uuid-$timestamp@leadflow.dev"
$adminPassword = "AdminPass@$(Get-Random -Maximum 9999)!"

$registerBody = @{
    name = "Test Admin User"
    email = $adminEmail
    password = $adminPassword
    confirmPassword = $adminPassword
} | ConvertTo-Json

try {
    $registerResponse = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/auth/register" `
        -Method POST `
        -Headers @{"Content-Type" = "application/json"} `
        -Body $registerBody `
        -ErrorAction Stop
    
    $registerData = $registerResponse.Content | ConvertFrom-Json
    $tenantId = $registerData.tenantId
    Write-Host "[OK] Usuário registrado com sucesso!" -ForegroundColor Green
    Write-Host "   Email: $adminEmail" -ForegroundColor Green
    Write-Host "   Tenant: $tenantId" -ForegroundColor Green
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "[ERROR] Registration failed. Status: $status" -ForegroundColor Red
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errorBody = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "   Error: $errorBody" -ForegroundColor Yellow
    } catch {}
    exit 1
}

# Step 1: Login with the new user
Write-Host "`n[STEP 1] Fazendo login com usuário criado..." -ForegroundColor Yellow
$loginBody = @{
    email = $adminEmail
    password = $adminPassword
    tenantId = $tenantId
} | ConvertTo-Json

try {
    $loginResponse = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/auth/login" `
        -Method POST `
        -Headers @{"Content-Type" = "application/json"} `
        -Body $loginBody `
        -ErrorAction Stop
    
    $loginData = $loginResponse.Content | ConvertFrom-Json
    $token = $loginData.accessToken
    Write-Host "[OK] Admin login bem sucedido!" -ForegroundColor Green
    Write-Host "   Email: $adminEmail" -ForegroundColor Green
    Write-Host "   Tenant: $tenantId" -ForegroundColor Green
    Write-Host "   Token: $($token.Substring(0,20))..." -ForegroundColor Cyan
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $errorBody = $reader.ReadToEnd()
    $reader.Close()
    Write-Host "[ERROR] Admin login failed. Status: $status" -ForegroundColor Red
    Write-Host "   Error: $errorBody" -ForegroundColor Yellow
    exit 1
}

# Step 1b: Promote user to ADMIN in database
Write-Host "`n[STEP 1b] Promovendo usuário para ADMIN no banco..." -ForegroundColor Yellow
$env:PGPASSWORD = "venusia"
$adminRoleId = "f638a022-0c74-46e8-a9bd-48070330b765"
try {
    $psqlOutput = & psql -h localhost -p 2411 -U postgres -d leadflowDB -t -c "UPDATE public.users SET role_id = '$adminRoleId' WHERE email = '$adminEmail' AND tenant_id = '$tenantId' RETURNING id, role_id;" 2>&1
    if ($psqlOutput -match "UPDATE|rows affected") {
        Write-Host "[OK] Usuário promovido para ADMIN" -ForegroundColor Green
    } else {
        Write-Host "[WARNING] Não conseguiu atualizar role_id" -ForegroundColor Yellow
    }
} catch {
    Write-Host "[WARNING] Erro ao atualizar role_id no banco - continuando..." -ForegroundColor Yellow
}

# Step 2: Validate token with GET /auth/me
Write-Host "`n[STEP 2] Validando token em /auth/me..." -ForegroundColor Yellow
try {
    $meResponse = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/auth/me" `
        -Method GET `
        -Headers @{
            "Authorization" = "Bearer $token"
            "Content-Type" = "application/json"
        } `
        -ErrorAction Stop
    
    $meData = $meResponse.Content | ConvertFrom-Json
    Write-Host "[OK] Token validado com sucesso!" -ForegroundColor Green
    Write-Host "   User ID: $($meData.id)" -ForegroundColor Green
    Write-Host "   Role: $($meData.role)" -ForegroundColor Green
    Write-Host "   Email: $($meData.email)" -ForegroundColor Green
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "[ERROR] Token validation failed. Status: $status" -ForegroundColor Red
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "   Error: $body" -ForegroundColor Yellow
    } catch {}
    Write-Host "   ⚠️  Token pode estar inválido" -ForegroundColor Yellow
}

# Step 2b: Re-login para atualizar claims (após promover para ADMIN)
Write-Host "`n[STEP 2b] Re-fazendo login após promoção para ADMIN..." -ForegroundColor Yellow
try {
    $reloginBody = @{
        email = $adminEmail
        password = $adminPassword
        tenantId = $tenantId
    } | ConvertTo-Json
    
    $reloginResponse = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/auth/login" `
        -Method POST `
        -Headers @{"Content-Type" = "application/json"} `
        -Body $reloginBody `
        -ErrorAction Stop
    
    $reloginData = $reloginResponse.Content | ConvertFrom-Json
    $token = $reloginData.accessToken
    Write-Host "[OK] Re-login bem sucedido com token atualizado!" -ForegroundColor Green
    Write-Host "   Token: $($token.Substring(0,20))..." -ForegroundColor Cyan
} catch {
    Write-Host "[WARNING] Re-login falhou - continuando com token antigo..." -ForegroundColor Yellow
}

Write-Host ""
Write-Host "Testando 5 endpoints admin:" -ForegroundColor Yellow
Write-Host ""

# 1. GET /admin/overview
Write-Host "[TEST 1] GET /admin/overview" -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/admin/overview" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $token"} `
        -ContentType "application/json" `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    Write-Host "   [OK] Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Response:" -ForegroundColor White
    $data | ConvertTo-Json | Write-Host
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "   [ERROR] Status: $status" -ForegroundColor Red
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "   Error: $body" -ForegroundColor Yellow
    } catch {}
}
Write-Host ""

# 2. GET /admin/metrics/growth?days=30
Write-Host "[TEST 2] GET /admin/metrics/growth?days=30" -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/admin/metrics/growth?days=30" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $token"} `
        -ContentType "application/json" `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    Write-Host "   [OK] Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Response:" -ForegroundColor White
    $data | ConvertTo-Json | Write-Host
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "   [ERROR] Status: $status" -ForegroundColor Red
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "   Error: $body" -ForegroundColor Yellow
    } catch {}
}
Write-Host ""

# 3. GET /admin/metrics/cohorts
Write-Host "[TEST 3] GET /admin/metrics/cohorts" -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/admin/metrics/cohorts" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $token"} `
        -ContentType "application/json" `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    Write-Host "   [OK] Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Response:" -ForegroundColor White
    $data | ConvertTo-Json | Write-Host
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "   [ERROR] Status: $status" -ForegroundColor Red
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "   Error: $body" -ForegroundColor Yellow
    } catch {}
}
Write-Host ""

# 4. GET /admin/metrics/forecast?months=6
Write-Host "[TEST 4] GET /admin/metrics/forecast?months=6" -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/admin/metrics/forecast?months=6" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $token"} `
        -ContentType "application/json" `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    Write-Host "   [OK] Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Response:" -ForegroundColor White
    $data | ConvertTo-Json | Write-Host
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "   [ERROR] Status: $status" -ForegroundColor Red
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "   Error: $body" -ForegroundColor Yellow
    } catch {}
}
Write-Host ""

# 5. GET /admin/metrics/health/{vendorId}
Write-Host "[TEST 5] GET /admin/metrics/health/{vendorId}" -ForegroundColor Cyan
Write-Host "   (Buscando um vendor ID no banco...)" -ForegroundColor Gray

# Buscar um vendor ID
$env:PGPASSWORD = "venusia"
$vendorOutput = & psql -h localhost -p 2411 -U postgres -d leadflowDB -t -c "SELECT id FROM public.vendors LIMIT 1;" 2>&1
$vendorId = ($vendorOutput | Out-String).Trim()

if ($vendorId -and $vendorId -ne "" -and $vendorId -ne "psql:") {
    Write-Host "   Vendor ID: $vendorId" -ForegroundColor Gray
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/admin/metrics/health/$vendorId" `
            -Method GET `
            -Headers @{"Authorization" = "Bearer $token"} `
            -ContentType "application/json" `
            -ErrorAction Stop
        
        $data = $response.Content | ConvertFrom-Json
        Write-Host "   [OK] Status: $($response.StatusCode)" -ForegroundColor Green
        Write-Host "   Response:" -ForegroundColor White
        $data | ConvertTo-Json | Write-Host
    } catch {
        $status = $_.Exception.Response.StatusCode.Value__
        Write-Host "   [ERROR] Status: $status" -ForegroundColor Red
        try {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $body = $reader.ReadToEnd()
            $reader.Close()
            Write-Host "   Error: $body" -ForegroundColor Yellow
        } catch {}
    }
} else {
    Write-Host "   [INFO] Nenhum vendor encontrado no banco" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "    TESTES COMPLETOS!" -ForegroundColor Green
Write-Host "===============================================" -ForegroundColor Cyan


