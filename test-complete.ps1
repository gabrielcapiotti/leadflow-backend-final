$BaseUrl = "http://localhost:8081"
$Email = "admin@leadflow.com"
$Pass = "password"

Write-Host "Logging in..." -ForegroundColor Yellow
$loginBody = @{ email = $Email; password = $Pass } | ConvertTo-Json
$login = curl -s -X POST "$BaseUrl/auth/login" -H "Content-Type: application/json" -d $loginBody | ConvertFrom-Json
$token = $login.token
$userId = $login.user.id
Write-Host "Got token and userId: $userId" -ForegroundColor Green

Write-Host ""
Write-Host "=== TEST 1: GET /api/me/settings (before)" -ForegroundColor Yellow
curl -s -X GET "$BaseUrl/api/me/settings" -H "Authorization: Bearer $token" -H "X-Tenant-ID: tenant-1" | ConvertFrom-Json | ConvertTo-Json

Write-Host ""
Write-Host "=== TEST 2: PUT /api/me/settings (create)" -ForegroundColor Yellow
$putBody = @{
    vendorName = "Vendor Test"
    whatsapp = "11999999999"
    companyName = "Company Test"
} | ConvertTo-Json
curl -s -X PUT "$BaseUrl/api/me/settings" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -H "X-Tenant-ID: tenant-1" -d $putBody | ConvertFrom-Json | ConvertTo-Json

Write-Host ""
Write-Host "=== TEST 3: GET /api/me/settings (after)" -ForegroundColor Yellow
curl -s -X GET "$BaseUrl/api/me/settings" -H "Authorization: Bearer $token" -H "X-Tenant-ID: tenant-1" | ConvertFrom-Json | ConvertTo-Json

Write-Host ""
Write-Host "=== TEST 4: PATCH /api/me/settings" -ForegroundColor Yellow
$patchBody = @{ companyName = "Updated Company" } | ConvertTo-Json
curl -s -X PATCH "$BaseUrl/api/me/settings" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -H "X-Tenant-ID: tenant-1" -d $patchBody | ConvertFrom-Json | ConvertTo-Json

Write-Host ""
Write-Host "=== TEST 5: GET /api/settings/{id}" -ForegroundColor Yellow
curl -s -X GET "$BaseUrl/api/settings/$userId" -H "Authorization: Bearer $token" -H "X-Tenant-ID: tenant-1" | ConvertFrom-Json | ConvertTo-Json

Write-Host ""
Write-Host "=== TEST 6: PUT /api/settings/{id}" -ForegroundColor Yellow
$adminBody = @{ vendorName = "Admin"; whatsapp = "11988888888"; companyName = "Admin Co" } | ConvertTo-Json
curl -s -X PUT "$BaseUrl/api/settings/$userId" -H "Authorization: Bearer $token" -H "Content-Type: application/json" -H "X-Tenant-ID: tenant-1" -d $adminBody | ConvertFrom-Json | ConvertTo-Json

Write-Host ""
Write-Host "=== TEST 7: GET /public/settings/{id} (NO AUTH)" -ForegroundColor Cyan
curl -s -X GET "$BaseUrl/public/settings/$userId" | ConvertFrom-Json | ConvertTo-Json

Write-Host ""
Write-Host "=== TEST 8: DELETE /api/settings/{id}" -ForegroundColor Yellow
curl -s -X DELETE "$BaseUrl/api/settings/$userId" -H "Authorization: Bearer $token" -H "X-Tenant-ID: tenant-1" -v 2>&1 | Select-String "< HTTP"

Write-Host ""
Write-Host "=== ALL TESTS COMPLETED ===" -ForegroundColor Green
