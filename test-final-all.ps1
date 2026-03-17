$BaseUrl = "http://localhost:8081"
$Email = "admin@leadflow.com"
$Pass = "password"

Write-Host "===== TESTE COMPLETO - TODOS OS ENDPOINTS DE SETTINGS =====" -ForegroundColor Cyan

# LOGIN
Write-Host "Autenticando..." -ForegroundColor Yellow
$loginJson = @{ email = $Email; password = $Pass } | ConvertTo-Json
$loginResp = Invoke-WebRequest -Uri "$BaseUrl/auth/login" -Method POST -ContentType "application/json" -Body $loginJson
$login = $loginResp.Content | ConvertFrom-Json
$token = $login.token
$userId = $login.user.id
Write-Host "OK - User ID: $userId`n" -ForegroundColor Green

# TEST 1
Write-Host "TEST 1: GET /api/me/settings (before create)" -ForegroundColor Yellow
$r = Invoke-WebRequest -Uri "$BaseUrl/api/me/settings" -Method GET -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"="tenant-1"} -ErrorAction SilentlyContinue
Write-Host "Status: 404 or 200 (expected before create)" -ForegroundColor Yellow

# TEST 2
Write-Host "TEST 2: PUT /api/me/settings (create)" -ForegroundColor Yellow
$putJson = @{
    vendorName = "Premium Vendor"
    whatsapp = "11999999999"
    companyName = "My Company Ltd"
} | ConvertTo-Json
$r = Invoke-WebRequest -Uri "$BaseUrl/api/me/settings" -Method PUT -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"="tenant-1"} -ContentType "application/json" -Body $putJson
$data = $r.Content | ConvertFrom-Json
Write-Host "Status: $($r.StatusCode) ✓" -ForegroundColor Green
Write-Host "Vendor: $($data.vendorName)`n" -ForegroundColor Cyan

# TEST 3
Write-Host "TEST 3: GET /api/me/settings (after create)" -ForegroundColor Yellow
$r = Invoke-WebRequest -Uri "$BaseUrl/api/me/settings" -Method GET -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"="tenant-1"}
$data = $r.Content | ConvertFrom-Json
Write-Host "Status: $($r.StatusCode) ✓" -ForegroundColor Green
Write-Host "Vendor: $($data.vendorName)`n" -ForegroundColor Cyan

# TEST 4
Write-Host "TEST 4: PATCH /api/me/settings (partial update)" -ForegroundColor Yellow
$patchJson = @{ companyName = "Updated Company Name" } | ConvertTo-Json
$r = Invoke-WebRequest -Uri "$BaseUrl/api/me/settings" -Method PATCH -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"="tenant-1"} -ContentType "application/json" -Body $patchJson
$data = $r.Content | ConvertFrom-Json
Write-Host "Status: $($r.StatusCode) ✓" -ForegroundColor Green
Write-Host "Company: $($data.companyName)`n" -ForegroundColor Cyan

# TEST 5
Write-Host "TEST 5: GET /api/settings/{id} (admin read)" -ForegroundColor Yellow
$r = Invoke-WebRequest -Uri "$BaseUrl/api/settings/$userId" -Method GET -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"="tenant-1"}
$data = $r.Content | ConvertFrom-Json
Write-Host "Status: $($r.StatusCode) ✓" -ForegroundColor Green
Write-Host "Vendor: $($data.vendorName)`n" -ForegroundColor Cyan

# TEST 6
Write-Host "TEST 6: PUT /api/settings/{id} (admin update)" -ForegroundColor Yellow
$adminJson = @{
    vendorName = "Admin Vendor Updated"
    whatsapp = "11988888888"
    companyName = "Admin Company"
} | ConvertTo-Json
$r = Invoke-WebRequest -Uri "$BaseUrl/api/settings/$userId" -Method PUT -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"="tenant-1"} -ContentType "application/json" -Body $adminJson
Write-Host "Status: $($r.StatusCode) ✓`n" -ForegroundColor Green

# TEST 7
Write-Host "TEST 7: GET /public/settings/{id} (PUBLIC - NO AUTH)" -ForegroundColor Cyan
$r = Invoke-WebRequest -Uri "$BaseUrl/public/settings/$userId" -Method GET
$data = $r.Content | ConvertFrom-Json
Write-Host "Status: $($r.StatusCode) ✓" -ForegroundColor Green
Write-Host "Vendor: $($data.vendorName)" -ForegroundColor Cyan
Write-Host "Company: $($data.companyName)`n" -ForegroundColor Cyan

# TEST 8
Write-Host "TEST 8: DELETE /api/settings/{id} (admin delete)" -ForegroundColor Yellow
$r = Invoke-WebRequest -Uri "$BaseUrl/api/settings/$userId" -Method DELETE -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"="tenant-1"}
Write-Host "Status: $($r.StatusCode) ✓ (204 No Content)`n" -ForegroundColor Green

Write-Host "===== TODOS OS TESTES COMPLETOS =====" -ForegroundColor Green
