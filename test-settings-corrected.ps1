# SETTINGS ENDPOINTS TEST - CORRECT ORDER
Write-Host "Settings Endpoints Test Suite" -ForegroundColor Cyan
Write-Host ""

# Get token
$loginUri = "http://localhost:8081/auth/login"
$loginBody = '{"email":"carlos@leadflow.com","password":"SenhaForte@123"}'

Write-Host "Getting auth token..." -ForegroundColor Yellow

try {
    $loginResponse = Invoke-WebRequest -Uri $loginUri -Method POST `
        -ContentType "application/json" -Body $loginBody -TimeoutSec 10
    $loginData = $loginResponse.Content | ConvertFrom-Json
    $token = $loginData.accessToken
    Write-Host "Token obtained successfully" -ForegroundColor Green
    Write-Host ""
} catch {
    Write-Host "Login failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
    "X-Tenant-ID" = "public"
}

# TEST 1 (Actually TEST 2): PUT /settings (CREATE first)
Write-Host "TEST 1: PUT /settings (Create settings)" -ForegroundColor Cyan
Write-Host "Create/update user settings" -ForegroundColor Gray
Write-Host ""

$updateBody = @{
    vendorName = "Carlos Silva Vendas"
    whatsapp = "5511987654321"
    companyName = "Silva Consultoria Ltda"
    logo = "https://example.com/carlos-logo.png"
    welcomeMessage = "Bem-vindo aos meus servicos!"
} | ConvertTo-Json

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081/settings" `
        -Method PUT -Headers $headers -Body $updateBody -TimeoutSec 10
    $data = $response.Content | ConvertFrom-Json
    Write-Host "Status: 200 OK" -ForegroundColor Green
    Write-Host $data | ConvertTo-Json
    $settingId = $data.id
    Write-Host ""
    Write-Host "Settings ID: $settingId" -ForegroundColor Green
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "Status: $status" -ForegroundColor Red
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    Write-Host $reader.ReadToEnd() -ForegroundColor Yellow
}

Write-Host ""
Write-Host "---" -ForegroundColor Gray
Write-Host ""

# TEST 2: GET /settings (NOW it should work)
Write-Host "TEST 2: GET /settings" -ForegroundColor Cyan
Write-Host "Get user settings" -ForegroundColor Gray
Write-Host ""

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081/settings" `
        -Method GET -Headers $headers -TimeoutSec 10
    $data = $response.Content | ConvertFrom-Json
    Write-Host "Status: 200 OK" -ForegroundColor Green
    $data | ConvertTo-Json | Write-Host
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "Status: $status" -ForegroundColor Red
}

Write-Host ""
Write-Host "---" -ForegroundColor Gray
Write-Host ""

# TEST 3: GET /settings/{id}
if ($settingId) {
    Write-Host "TEST 3: GET /settings/{id}" -ForegroundColor Cyan
    Write-Host "Get settings by ID" -ForegroundColor Gray
    Write-Host ""

    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8081/settings/$settingId" `
            -Method GET -Headers $headers -TimeoutSec 10
        $data = $response.Content | ConvertFrom-Json
        Write-Host "Status: 200 OK" -ForegroundColor Green
        $data | ConvertTo-Json | Write-Host
    } catch {
        $status = $_.Exception.Response.StatusCode.Value__
        Write-Host "Status: $status" -ForegroundColor Red
    }
} else {
    Write-Host "TEST 3: SKIPPED (no ID from previous test)" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "---" -ForegroundColor Gray
Write-Host ""

# TEST 4: DELETE /settings
Write-Host "TEST 4: DELETE /settings" -ForegroundColor Cyan
Write-Host "Delete user settings (soft delete)" -ForegroundColor Gray
Write-Host ""

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081/settings" `
        -Method DELETE -Headers $headers -TimeoutSec 10
    Write-Host "Status: 204 No Content" -ForegroundColor Green
    Write-Host "Settings deleted successfully (soft delete)" -ForegroundColor Green
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "Status: $status" -ForegroundColor Red
}

Write-Host ""
Write-Host "Tests completed" -ForegroundColor Cyan
