Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "    TESTANDO ENDPOINTS DO ADMIN" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8081/api"
$adminEmail = "user.test.20260330140708324@test.com"
$adminPassword = "Test@123"
$tenantId = "a5c742bc-c670-4755-bc60-6ac78142a623"

# Step 1: Login with the admin user
Write-Host "[STEP 1] Fazendo login com usuário admin..." -ForegroundColor Yellow
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
$vendorOutput = psql -h localhost -p 2411 -U postgres -d leadflow_test -t -c "SELECT id FROM public.vendors LIMIT 1;" 2>&1
$vendorId = $vendorOutput.Trim()

if ($vendorId -and $vendorId -ne "") {
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


