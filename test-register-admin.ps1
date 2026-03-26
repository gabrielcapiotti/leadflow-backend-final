Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "    TESTANDO /auth/register-admin" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

$baseUrl = "http://localhost:8081"
$internalSecret = "SUPER_SECRET_KEY_CHANGE_ME"
$tenantId = "49c868d1-4da0-420e-8e0e-7b063dcc7390"
$adminEmail = "admin.root@leadflow.com"
$adminPassword = "AdminRoot@123"

# =====================================
# Teste 1: Registar admin com secret correto
# =====================================
Write-Host "[TEST 1] POST /auth/register-admin (com secret correto)" -ForegroundColor Yellow
Write-Host ""

$regBody = @{
    name = "Admin Root"
    email = $adminEmail
    password = $adminPassword
    confirmPassword = $adminPassword
} | ConvertTo-Json

Write-Host "Request Headers:" -ForegroundColor Gray
Write-Host "  X-Tenant-ID: $tenantId" -ForegroundColor Gray
Write-Host "  X-Internal-Secret: $internalSecret" -ForegroundColor Gray
Write-Host ""
Write-Host "Request Body:" -ForegroundColor Gray
Write-Host $regBody -ForegroundColor Gray
Write-Host ""

try {
    $headers = @{
        "Content-Type" = "application/json"
        "X-Tenant-ID" = $tenantId
        "X-Internal-Secret" = $internalSecret
    }
    
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/auth/register-admin" `
        -Method POST `
        -Headers $headers `
        -Body $regBody `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    
    Write-Host "[✅ SUCCESS] Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "   Response:" -ForegroundColor Green
    Write-Host "   - accessToken: $($data.accessToken.Substring(0,20))..." -ForegroundColor Green
    Write-Host "   - refreshToken: $($data.refreshToken.Substring(0,20))..." -ForegroundColor Green
    Write-Host "   - tenant: $($data.tenant)" -ForegroundColor Green
    $token = $data.accessToken
    
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    $errorBody = $reader.ReadToEnd()
    $reader.Close()
    Write-Host "[❌ FAILED] Status: $status" -ForegroundColor Red
    Write-Host "   Error: $errorBody" -ForegroundColor Yellow
    exit 1
}

Write-Host ""
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

# =====================================
# Teste 2: Falhar sem secret
# =====================================
Write-Host "[TEST 2] POST /auth/register-admin (SEM secret - deve falhar)" -ForegroundColor Yellow
Write-Host ""

try {
    $headers = @{
        "Content-Type" = "application/json"
        "X-Tenant-ID" = $tenantId
    }
    
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/auth/register-admin" `
        -Method POST `
        -Headers $headers `
        -Body $regBody `
        -ErrorAction Stop
    
    Write-Host "[❌ ERROR] Deveria ter falhado mas passou!" -ForegroundColor Red
    
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    if ($status -eq 401 -or $status -eq 403) {
        Write-Host "[✅ EXPECTED] Status: $status (Unauthorized/Forbidden)" -ForegroundColor Green
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errorBody = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "   Message: $errorBody" -ForegroundColor Green
    } else {
        Write-Host "[❌ UNEXPECTED] Status: $status" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

# =====================================
# Teste 3: Falhar com secret errado
# =====================================
Write-Host "[TEST 3] POST /auth/register-admin (com secret ERRADO - deve falhar)" -ForegroundColor Yellow
Write-Host ""

try {
    $headers = @{
        "Content-Type" = "application/json"
        "X-Tenant-ID" = $tenantId
        "X-Internal-Secret" = "WRONG_SECRET"
    }
    
    $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/auth/register-admin" `
        -Method POST `
        -Headers $headers `
        -Body $regBody `
        -ErrorAction Stop
    
    Write-Host "[❌ ERROR] Deveria ter falhado mas passou!" -ForegroundColor Red
    
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    if ($status -eq 401 -or $status -eq 403) {
        Write-Host "[✅ EXPECTED] Status: $status (Unauthorized/Forbidden)" -ForegroundColor Green
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errorBody = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "   Message: $errorBody" -ForegroundColor Green
    } else {
        Write-Host "[❌ UNEXPECTED] Status: $status" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host ""

# =====================================
# Teste 4: Acessar /admin/overview com token admin
# =====================================
Write-Host "[TEST 4] GET /admin/overview (com token do admin registrado)" -ForegroundColor Yellow
Write-Host ""

if ($null -ne $token) {
    try {
        $response = Invoke-WebRequest -UseBasicParsing -Uri "$baseUrl/admin/overview" `
            -Method GET `
            -Headers @{
                "Authorization" = "Bearer $token"
                "X-Tenant-ID" = $tenantId
            } `
            -ErrorAction Stop
        
        $data = $response.Content | ConvertFrom-Json
        Write-Host "[✅ SUCCESS] Status: $($response.StatusCode)" -ForegroundColor Green
        Write-Host "   Admin endpoint is accessible!" -ForegroundColor Green
        Write-Host "   Response: $($data | ConvertTo-Json)" -ForegroundColor Cyan
        
    } catch {
        $status = $_.Exception.Response.StatusCode.Value__
        Write-Host "[❌ FAILED] Status: $status" -ForegroundColor Red
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $errorBody = $reader.ReadToEnd()
        $reader.Close()
        Write-Host "   Error: $errorBody" -ForegroundColor Yellow
    }
} else {
    Write-Host "[⚠️  SKIPPED] Nenhum token disponível" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "===============================================" -ForegroundColor Cyan
Write-Host "    TESTES CONCLUÍDOS!" -ForegroundColor Green
Write-Host "===============================================" -ForegroundColor Cyan
