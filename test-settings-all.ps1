# SETTINGS ENDPOINTS TEST
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
}

# Test 1: GET /api/settings
Write-Host "TEST 1: GET /api/settings" -ForegroundColor Cyan
Write-Host "Get user settings" -ForegroundColor Gray
Write-Host ""

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081/api/settings" `
        -Method GET -Headers $headers -TimeoutSec 10
    $data = $response.Content | ConvertFrom-Json
    Write-Host "Status: 200 OK" -ForegroundColor Green
    $data | ConvertTo-Json | Write-Host
    $settingId = $data.id
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "Status: $status" -ForegroundColor Red
}

Write-Host ""
Write-Host "---" -ForegroundColor Gray
Write-Host ""

# Test 2: PUT /api/settings
Write-Host "TEST 2: PUT /api/settings" -ForegroundColor Cyan
Write-Host "Update user settings" -ForegroundColor Gray
Write-Host ""

$updateBody = @{
    vendorName = "Vendor Test Updated"
    whatsapp = "5511988776655"
    companyName = "Empresa Teste Ltda"
    logo = "https://example.com/logo.png"
    welcomeMessage = "Welcome to our service!"
} | ConvertTo-Json

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081/api/settings" `
        -Method PUT -Headers $headers -Body $updateBody -TimeoutSec 10
    $data = $response.Content | ConvertFrom-Json
    Write-Host "Status: 200 OK" -ForegroundColor Green
    $data | ConvertTo-Json | Write-Host
    $settingId = $data.id
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "Status: $status" -ForegroundColor Red
    $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
    Write-Host $reader.ReadToEnd() -ForegroundColor Yellow
}

Write-Host ""
Write-Host "---" -ForegroundColor Gray
Write-Host ""

# Test 3: GET /api/settings/{id}
if ($settingId) {
    Write-Host "TEST 3: GET /api/settings/{id}" -ForegroundColor Cyan
    Write-Host "Get settings by ID" -ForegroundColor Gray
    Write-Host ""

    try {
        $response = Invoke-WebRequest -Uri "http://localhost:8081/api/settings/$settingId" `
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

# Test 4: DELETE /api/settings
Write-Host "TEST 4: DELETE /api/settings" -ForegroundColor Cyan
Write-Host "Delete user settings (soft delete)" -ForegroundColor Gray
Write-Host ""

try {
    $response = Invoke-WebRequest -Uri "http://localhost:8081/api/settings" `
        -Method DELETE -Headers $headers -TimeoutSec 10
    Write-Host "Status: 204 No Content" -ForegroundColor Green
    Write-Host "Settings deleted successfully (soft delete)" -ForegroundColor Green
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "Status: $status" -ForegroundColor Red
}

Write-Host ""
Write-Host "Tests completed" -ForegroundColor Cyan
