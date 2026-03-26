Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "    TESTANDO ENDPOINTS DO ADMIN" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8081"
$adminEmail = "admin.tester@leadflow.com"
$adminPassword = "AdminTest@123"

# Tentar registrar novo admin (ou usar se ja existe)
Write-Host "[REGISTER] Registrando novo admin..." -ForegroundColor Yellow
$regBody = @{
    name = "Admin Tester"
    email = $adminEmail
    password = $adminPassword
    confirmPassword = $adminPassword
} | ConvertTo-Json

$tenantId = $null
try {
    $regResponse = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/auth/register" `
        -Method POST `
        -Headers @{"Content-Type" = "application/json"} `
        -Body $regBody `
        -ErrorAction Stop
    Write-Host "[OK] Admin criado" -ForegroundColor Green
    
    # Capturar tenantId da resposta
    $regData = $regResponse.Content | ConvertFrom-Json
    $tenantId = $regData.tenantId
    Write-Host "   Tenant ID: $tenantId" -ForegroundColor Green
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    if ($status -eq 409) {
        Write-Host "[INFO] Status 409 - Usuario ja existe" -ForegroundColor Yellow
        # Buscar tenant ID do usuario existente no banco
        Write-Host "   Consultando banco para obter Tenant ID..." -ForegroundColor Cyan
        $env:PGPASSWORD = "venusia"
        $tenantIdFromDb = psql -h localhost -p 2411 -U postgres -d leadflow_test -t -c "SELECT CAST(tenant_id AS VARCHAR) FROM public.users WHERE email = '$adminEmail' LIMIT 1;" 2>&1
        $tenantId = $tenantIdFromDb.Trim()
        if ($tenantId) {
            Write-Host "   [OK] Tenant ID encontrado: $tenantId" -ForegroundColor Green
        } else {
            Write-Host "   [ERROR] Nao foi possivel obter o Tenant ID do banco" -ForegroundColor Red
            exit 1
        }
    } else {
        Write-Host "[ERROR] Erro inesperado: Status $status" -ForegroundColor Red
        try {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $errorDetails = $reader.ReadToEnd()
            $reader.Close()
            Write-Host "   Details: $errorDetails" -ForegroundColor Yellow
        } catch {}
        exit 1
    }
}

Write-Host ""
Write-Host "[LOGIN] Realizando login..." -ForegroundColor Yellow

$loginBody = @{
    email = $adminEmail
    password = $adminPassword
} | ConvertTo-Json

$token = $null
try {
    # Enviar tenantId no header
    $loginHeaders = @{
        "Content-Type" = "application/json"
        "X-Tenant-ID" = $tenantId
    }
    Write-Host "   Enviando Tenant ID: $tenantId" -ForegroundColor Cyan
    
    $loginResponse = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/auth/login" `
        -Method POST `
        -Headers $loginHeaders `
        -Body $loginBody `
        -ErrorAction Stop
    
    $loginData = $loginResponse.Content | ConvertFrom-Json
    $token = $loginData.accessToken
    Write-Host "[OK] Login realizado com sucesso" -ForegroundColor Green
    Write-Host "   Token: $($token.Substring(0,20))..." -ForegroundColor Cyan
    
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $errorBody = $reader.ReadToEnd()
    $reader.Close()
    Write-Host "[ERROR] Erro de Login: $status" -ForegroundColor Red
    Write-Host "   Detalhes: $errorBody" -ForegroundColor Yellow
    exit 1
}

if ($null -eq $token) {
    Write-Host "[ERROR] Token nao obtido" -ForegroundColor Red
    exit 1
}

Write-Host ""
Write-Host "Testando 5 endpoints admin:" -ForegroundColor Yellow
Write-Host ""

# 1. GET /admin/overview
Write-Host "[TEST 1] GET /admin/overview" -ForegroundColor Cyan
try {
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/admin/overview" `
        -Method GET `
        -Headers @{"Authorization" = "Bearer $token"; "X-Tenant-ID" = $tenantId} `
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
        -Headers @{"Authorization" = "Bearer $token"; "X-Tenant-ID" = $tenantId} `
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
        -Headers @{"Authorization" = "Bearer $token"; "X-Tenant-ID" = $tenantId} `
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
        -Headers @{"Authorization" = "Bearer $token"; "X-Tenant-ID" = $tenantId} `
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
$vendorOutput = psql -h localhost -p 2411 -U postgres -d leadflow_test -t -c "SELECT id FROM public.vendors LIMIT 1;" 2>&1
$vendorId = $vendorOutput.Trim()

if ($vendorId -and $vendorId -ne "") {
    Write-Host "   Vendor ID: $vendorId" -ForegroundColor Gray
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/admin/metrics/health/$vendorId" `
            -Method GET `
            -Headers @{"Authorization" = "Bearer $token"; "X-Tenant-ID" = $tenantId} `
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
