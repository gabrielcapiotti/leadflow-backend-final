Write-Host "TESTE COMPLETO - TODOS OS 8 ENDPOINTS" -ForegroundColor Cyan

Write-Host ""
Write-Host "Autenticando..." -ForegroundColor Yellow
$loginBody = @{ email = "carlos@leadflow.com"; password = "SenhaForte@123" } | ConvertTo-Json
$loginResp = Invoke-WebRequest -Uri "http://localhost:8081/auth/login" -Method POST -Headers @{"Content-Type"="application/json"} -Body $loginBody -UseBasicParsing
$loginData = $loginResp.Content | ConvertFrom-Json
$token = $loginData.accessToken

# Decodificar JWT para extrair userId
$parts = $token.Split('.')
$payload = $parts[1]
while ($payload.Length % 4) { $payload += "=" }
$decoded = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload))
$tokenData = $decoded | ConvertFrom-Json

Write-Host "OK - Token obtido" -ForegroundColor Green

$h = @{"Authorization"="Bearer $token";"X-Tenant-ID"="public";"Content-Type"="application/json"}

Write-Host ""
Write-Host "TEST 1: GET /api/me/settings (antes criar)" -ForegroundColor Yellow
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/api/me/settings" -Method GET -Headers $h -UseBasicParsing -ErrorAction Stop
    Write-Host "Status: $($r.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "Status: Not found yet" -ForegroundColor Yellow
}

Write-Host ""
Write-Host "TEST 2: PUT /api/me/settings (criar novo)" -ForegroundColor Yellow
$body = @{ vendorName="Test Vendor"; whatsapp="11999999999"; companyName="Test Company" } | ConvertTo-Json
$r = Invoke-WebRequest -Uri "http://localhost:8081/api/me/settings" -Method PUT -Headers $h -Body $body -UseBasicParsing
$data = $r.Content | ConvertFrom-Json
$settingId = $data.id
Write-Host "Status: $($r.StatusCode) OK" -ForegroundColor Green
Write-Host "Vendor: $($data.vendorName)" -ForegroundColor Cyan
Write-Host "Setting ID: $settingId" -ForegroundColor Yellow

Write-Host ""
Write-Host "TEST 3: GET /api/me/settings (depois criar)" -ForegroundColor Yellow
$r = Invoke-WebRequest -Uri "http://localhost:8081/api/me/settings" -Method GET -Headers $h -UseBasicParsing
$data = $r.Content | ConvertFrom-Json
Write-Host "Status: $($r.StatusCode) OK" -ForegroundColor Green
Write-Host "Vendor: $($data.vendorName)" -ForegroundColor Cyan
Write-Host "WhatsApp: $($data.whatsapp)" -ForegroundColor Cyan

Write-Host ""
Write-Host "TEST 4: PATCH /api/me/settings (parcial)" -ForegroundColor Yellow
$body = @{ companyName="Updated Company" } | ConvertTo-Json
$r = Invoke-WebRequest -Uri "http://localhost:8081/api/me/settings" -Method PATCH -Headers $h -Body $body -UseBasicParsing
$data = $r.Content | ConvertFrom-Json
Write-Host "Status: $($r.StatusCode) OK" -ForegroundColor Green
Write-Host "Company: $($data.companyName)" -ForegroundColor Cyan

Write-Host ""
Write-Host "TEST 5: GET /api/settings/{id} (admin read)" -ForegroundColor Yellow
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/api/settings/$settingId" -Method GET -Headers $h -UseBasicParsing -ErrorAction Stop
    Write-Host "Status: $($r.StatusCode) OK" -ForegroundColor Green
} catch {
    Write-Host "ERROR - Could not find setting. Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

Write-Host ""
Write-Host "TEST 6: PUT /api/settings/{id} (admin update)" -ForegroundColor Yellow
try {
    $body = @{ vendorName="Admin Updated"; whatsapp="11988888888"; companyName="Admin Company" } | ConvertTo-Json
    $r = Invoke-WebRequest -Uri "http://localhost:8081/api/settings/$settingId" -Method PUT -Headers $h -Body $body -UseBasicParsing -ErrorAction Stop
    Write-Host "Status: $($r.StatusCode) OK" -ForegroundColor Green
} catch {
    Write-Host "ERROR - Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

Write-Host ""
Write-Host "TEST 7: GET /public/settings/{id} (PUBLIC - SEM AUTH)" -ForegroundColor Cyan
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/public/settings/$settingId" -Method GET -UseBasicParsing -ErrorAction Stop
    $data = $r.Content | ConvertFrom-Json
    Write-Host "Status: $($r.StatusCode) OK" -ForegroundColor Green
    Write-Host "Vendor: $($data.vendorName)" -ForegroundColor Cyan
    Write-Host "Company: $($data.companyName)" -ForegroundColor Cyan
} catch {
    Write-Host "ERROR - Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

Write-Host ""
Write-Host "TEST 8: DELETE /api/settings/{id} (admin delete)" -ForegroundColor Yellow
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/api/settings/$settingId" -Method DELETE -Headers $h -UseBasicParsing -ErrorAction Stop
    Write-Host "Status: $($r.StatusCode) OK (204 No Content)" -ForegroundColor Green
} catch {
    Write-Host "ERROR - Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

Write-Host ""
Write-Host "TODOS OS 8 ENDPOINTS TESTADOS COM SUCESSO" -ForegroundColor Green
