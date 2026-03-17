$BaseUrl = "http://localhost:8081"
$Email = "admin@leadflow.com"
$Pass = "password"

Write-Host "TESTE COMPLETO - TODOS OS ENDPOINTS" -ForegroundColor Cyan

$loginJson = @{ email = $Email; password = $Pass } | ConvertTo-Json
$loginResp = Invoke-WebRequest -Uri "$BaseUrl/auth/login" -Method POST -ContentType "application/json" -Body $loginJson
$login = $loginResp.Content | ConvertFrom-Json
$token = $login.token
$userId = $login.user.id
Write-Host "User ID: $userId" -ForegroundColor Green
Write-Host ""

Write-Host "TEST 1: GET /api/me/settings" -ForegroundColor Yellow
$r = Invoke-WebRequest -Uri "$BaseUrl/api/me/settings" -Method GET -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"="tenant-1"} -ErrorAction SilentlyContinue
Write-Host "Status: $($r.StatusCode)"
Write-Host ""

Write-Host "TEST 2: PUT /api/me/settings" -ForegroundColor Yellow
$putJson = @{ vendorName = "Test Vendor"; whatsapp = "11999999999"; companyName = "Test Co" } | ConvertTo-Json
$r = Invoke-WebRequest -Uri "$BaseUrl/api/me/settings" -Method PUT -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"="tenant-1"} -ContentType "application/json" -Body $putJson
Write-Host "Status: $($r.StatusCode)"
Write-Host ""

Write-Host "TEST 3: GET /api/me/settings (after put)" -ForegroundColor Yellow
$r = Invoke-WebRequest -Uri "$BaseUrl/api/me/settings" -Method GET -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"="tenant-1"}
$data = $r.Content | ConvertFrom-Json
Write-Host "Status: $($r.StatusCode) - Vendor: $($data.vendorName)"
Write-Host ""

Write-Host "TEST 4: PATCH /api/me/settings" -ForegroundColor Yellow
$patchJson = @{ companyName = "Updated Company" } | ConvertTo-Json
$r = Invoke-WebRequest -Uri "$BaseUrl/api/me/settings" -Method PATCH -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"="tenant-1"} -ContentType "application/json" -Body $patchJson
Write-Host "Status: $($r.StatusCode)"
Write-Host ""

Write-Host "TEST 5: GET /api/settings/{id}" -ForegroundColor Yellow
$r = Invoke-WebRequest -Uri "$BaseUrl/api/settings/$userId" -Method GET -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"="tenant-1"}
Write-Host "Status: $($r.StatusCode)"
Write-Host ""

Write-Host "TEST 6: PUT /api/settings/{id}" -ForegroundColor Yellow
$adminJson = @{ vendorName = "Admin Vendor"; whatsapp = "11988888888"; companyName = "Admin Co" } | ConvertTo-Json
$r = Invoke-WebRequest -Uri "$BaseUrl/api/settings/$userId" -Method PUT -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"="tenant-1"} -ContentType "application/json" -Body $adminJson
Write-Host "Status: $($r.StatusCode)"
Write-Host ""

Write-Host "TEST 7: GET /public/settings/{id} (NO AUTH)" -ForegroundColor Cyan
$r = Invoke-WebRequest -Uri "$BaseUrl/public/settings/$userId" -Method GET
$data = $r.Content | ConvertFrom-Json
Write-Host "Status: $($r.StatusCode)"
Write-Host "Vendor: $($data.vendorName)"
Write-Host "Company: $($data.companyName)"
Write-Host ""

Write-Host "TEST 8: DELETE /api/settings/{id}" -ForegroundColor Yellow
$r = Invoke-WebRequest -Uri "$BaseUrl/api/settings/$userId" -Method DELETE -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"="tenant-1"}
Write-Host "Status: $($r.StatusCode)"
Write-Host ""

Write-Host "TODOS OS TESTES CONCLUIDOS" -ForegroundColor Green
