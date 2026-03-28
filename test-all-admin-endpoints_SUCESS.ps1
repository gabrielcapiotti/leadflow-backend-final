Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "    TESTANDO ENDPOINTS DO ADMIN" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8081/api"
$adminEmail = "admin.registered@leadflow.com"
$adminPassword = "AdminTest@123"
$adminName = "Admin Registered"

# Step 1: Registrar um usuário normal para usar como base
Write-Host "[STEP 1] Registrando usuário base..." -ForegroundColor Yellow
$baseUserEmail = "base.user@leadflow.com"
$baseUserPassword = "BaseUser@123"

$regBody = @{
    name = "Base User"
    email = $baseUserEmail
    password = $baseUserPassword
    confirmPassword = $baseUserPassword
} | ConvertTo-Json

try {
    $baseRegResponse = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/auth/register" `
        -Method POST `
        -Headers @{"Content-Type" = "application/json"} `
        -Body $regBody `
        -ErrorAction Stop
    
    $baseData = $baseRegResponse.Content | ConvertFrom-Json
    $baseTenantId = $baseData.tenantId
    $baseToken = $baseData.accessToken
    Write-Host "[OK] Base user criado" -ForegroundColor Green
    Write-Host "   Tenant ID: $baseTenantId" -ForegroundColor Green
    Write-Host "   Token: $($baseToken.Substring(0,20))..." -ForegroundColor Cyan
} catch {
    Write-Host "[ERROR] Falha ao registrar base user" -ForegroundColor Red
    exit 1
}

# Step 2: Usar o base user (agora como admin) para registrar um novo admin
Write-Host ""
Write-Host "[STEP 2] Registrando novo admin via /auth/register-admin..." -ForegroundColor Yellow

$adminRegBody = @{
    name = $adminName
    email = $adminEmail
    password = $adminPassword
    confirmPassword = $adminPassword
} | ConvertTo-Json

try {
    # Usar o token do base user como admin (vamos mudar seu role no banco depois)
    $adminHeaders = @{
        "Content-Type" = "application/json"
        "Authorization" = "Bearer $baseToken"
        "X-Tenant-ID" = $baseTenantId
    }

    $adminRegResponse = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/auth/register-admin" `
        -Method POST `
        -Headers $adminHeaders `
        -Body $adminRegBody `
        -ErrorAction Stop
    
    $adminData = $adminRegResponse.Content | ConvertFrom-Json
    $tenantId = $adminData.tenantId
    $token = $adminData.accessToken
    Write-Host "[OK] Admin registrado com sucesso!" -ForegroundColor Green
    Write-Host "   Email: $adminEmail" -ForegroundColor Green
    Write-Host "   Tenant ID: $tenantId" -ForegroundColor Green
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "[WARNING] Status $status - Admin registration via authenticated route não funcionou" -ForegroundColor Yellow
    Write-Host "        Tentando via X-Internal-Secret..." -ForegroundColor Cyan
    
    # Fallback: Usar secret method
    try {
        $secretHeaders = @{
            "Content-Type" = "application/json"
            "X-Internal-Secret" = "SUPER_SECRET_KEY_CHANGE_ME"
            "X-Tenant-ID" = $baseTenantId
        }
        
        $adminRegResponse = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/auth/register-admin" `
            -Method POST `
            -Headers $secretHeaders `
            -Body $adminRegBody `
            -ErrorAction Stop
        
        $adminData = $adminRegResponse.Content | ConvertFrom-Json
        $tenantId = $adminData.tenantId
        $token = $adminData.accessToken
        Write-Host "[OK] Admin registrado via X-Internal-Secret!" -ForegroundColor Green
        Write-Host "   Email: $adminEmail" -ForegroundColor Green
    } catch {
        $status = $_.Exception.Response.StatusCode.Value__
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errorBody = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "[ERROR] Ambas as estratégias falharam. Status: $status" -ForegroundColor Red
        Write-Host "   Error: $errorBody" -ForegroundColor Yellow
        exit 1
    }
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
