$BaseUrl = "http://localhost:8081"
$AdminEmail = "admin@leadflow.com"
$AdminPassword = "password"

Write-Host "=== TESTE COMPLETO - TODOS OS ENDPOINTS DE SETTINGS ===" -ForegroundColor Cyan

# 1. LOGIN
Write-Host "[1/9] Fazendo login..." -ForegroundColor Yellow
$loginBody = @{
    email = $AdminEmail
    password = $AdminPassword
} | ConvertTo-Json

$loginResp = curl -s -X POST "$BaseUrl/auth/login" `
    -H "Content-Type: application/json" `
    -d $loginBody

$loginJson = $loginResp | ConvertFrom-Json
$token = $loginJson.token
$userId = $loginJson.user.id

Write-Host "✓ Token obtido: $($token.Substring(0, 20))..." -ForegroundColor Green
Write-Host "  User ID: $userId"

# 2. GET /api/me/settings (antes de criar)
Write-Host "`n[2/9] GET /api/me/settings (antes de criar)..." -ForegroundColor Yellow
$getResp1 = curl -s -X GET "$BaseUrl/api/me/settings" `
    -H "Authorization: Bearer $token" `
    -H "X-Tenant-ID: tenant-1" `
    -w "`n%{http_code}"

$status1 = $getResp1[-1]
$body1 = $getResp1[0..($getResp1.Count-2)] -join ""

if ($status1 -eq "404" -or $status1 -eq "200") {
    Write-Host "✓ Status $status1" -ForegroundColor Green
} else {
    Write-Host "✗ Status $status1" -ForegroundColor Red
}

# 3. PUT /api/me/settings (criar novo)
Write-Host "`n[3/9] PUT /api/me/settings (criar novo)..." -ForegroundColor Yellow
$putBody = @{
    vendorName = "Premium Vendor"
    whatsapp = "11999999999"
    companyName = "My Company"
    logo = "https://example.com/logo.png"
    welcomeMessage = "Welcome to our settings!"
} | ConvertTo-Json

$putResp = curl -s -X PUT "$BaseUrl/api/me/settings" `
    -H "Authorization: Bearer $token" `
    -H "Content-Type: application/json" `
    -H "X-Tenant-ID: tenant-1" `
    -d $putBody `
    -w "`n%{http_code}"

$putStatus = $putResp[-1]
$putBody = $putResp[0..($putResp.Count-2)] -join ""
$putJson = $putBody | ConvertFrom-Json

if ($putStatus -eq "200") {
    Write-Host "✓ Status $putStatus" -ForegroundColor Green
    Write-Host "  Vendor: $($putJson.vendorName)"
} else {
    Write-Host "✗ Status $putStatus" -ForegroundColor Red
    Write-Host "  Response: $putBody"
}

# 4. GET /api/me/settings (após criar)
Write-Host "`n[4/9] GET /api/me/settings (após criar)..." -ForegroundColor Yellow
$getResp2 = curl -s -X GET "$BaseUrl/api/me/settings" `
    -H "Authorization: Bearer $token" `
    -H "X-Tenant-ID: tenant-1" `
    -w "`n%{http_code}"

$status2 = $getResp2[-1]
$body2 = $getResp2[0..($getResp2.Count-2)] -join ""
$json2 = $body2 | ConvertFrom-Json

if ($status2 -eq "200") {
    Write-Host "✓ Status $status2" -ForegroundColor Green
    Write-Host "  Vendor: $($json2.vendorName)"
    Write-Host "  WhatsApp: $($json2.whatsapp)"
} else {
    Write-Host "✗ Status $status2" -ForegroundColor Red
}

# 5. PATCH /api/me/settings (atualizar parcial)
Write-Host "`n[5/9] PATCH /api/me/settings (atualizar parcial)..." -ForegroundColor Yellow
$patchBody = @{
    companyName = "Updated Company Name"
    welcomeMessage = "Updated welcome!"
} | ConvertTo-Json

$patchResp = curl -s -X PATCH "$BaseUrl/api/me/settings" `
    -H "Authorization: Bearer $token" `
    -H "Content-Type: application/json" `
    -H "X-Tenant-ID: tenant-1" `
    -d $patchBody `
    -w "`n%{http_code}"

$patchStatus = $patchResp[-1]
$patchBodyResp = $patchResp[0..($patchResp.Count-2)] -join ""

if ($patchStatus -eq "200") {
    Write-Host "✓ Status $patchStatus" -ForegroundColor Green
} else {
    Write-Host "✗ Status $patchStatus" -ForegroundColor Red
}

# 6. GET /api/settings/{id} (admin read)
Write-Host "`n[6/9] GET /api/settings/{id} (admin read)..." -ForegroundColor Yellow
$getAdminResp = curl -s -X GET "$BaseUrl/api/settings/$userId" `
    -H "Authorization: Bearer $token" `
    -H "X-Tenant-ID: tenant-1" `
    -w "`n%{http_code}"

$adminStatus = $getAdminResp[-1]
$adminBody = $getAdminResp[0..($getAdminResp.Count-2)] -join ""

if ($adminStatus -eq "200") {
    Write-Host "✓ Status $adminStatus" -ForegroundColor Green
} else {
    Write-Host "✗ Status $adminStatus" -ForegroundColor Red
}

# 7. PUT /api/settings/{id} (admin update)
Write-Host "`n[7/9] PUT /api/settings/{id} (admin update)..." -ForegroundColor Yellow
$putAdminBody = @{
    vendorName = "Admin Updated Vendor"
    whatsapp = "11988888888"
    companyName = "Admin Updated Company"
} | ConvertTo-Json

$putAdminResp = curl -s -X PUT "$BaseUrl/api/settings/$userId" `
    -H "Authorization: Bearer $token" `
    -H "Content-Type: application/json" `
    -H "X-Tenant-ID: tenant-1" `
    -d $putAdminBody `
    -w "`n%{http_code}"

$putAdminStatus = $putAdminResp[-1]
$putAdminBodyResp = $putAdminResp[0..($putAdminResp.Count-2)] -join ""

if ($putAdminStatus -eq "200") {
    Write-Host "✓ Status $putAdminStatus" -ForegroundColor Green
} else {
    Write-Host "✗ Status $putAdminStatus" -ForegroundColor Red
}

# 8. GET /public/settings/{id} (public read - sem auth)
Write-Host "`n[8/9] GET /public/settings/{id} (public read - SEM AUTH)..." -ForegroundColor Yellow
$publicResp = curl -s -X GET "$BaseUrl/public/settings/$userId" `
    -H "Content-Type: application/json" `
    -w "`n%{http_code}"

$publicStatus = $publicResp[-1]
$publicBody = $publicResp[0..($publicResp.Count-2)] -join ""
$publicJson = $publicBody | ConvertFrom-Json 2>$null

if ($publicStatus -eq "200") {
    Write-Host "✓ Status $publicStatus" -ForegroundColor Green
    Write-Host "  Vendor: $($publicJson.vendorName)" -ForegroundColor Cyan
    Write-Host "  Company: $($publicJson.companyName)" -ForegroundColor Cyan
} else {
    Write-Host "✗ Status $publicStatus" -ForegroundColor Red
    Write-Host "  Response: $publicBody"
}

# 9. DELETE /api/me/settings (delete own)
Write-Host "`n[9/9] DELETE /api/me/settings (delete own - 204 expected)..." -ForegroundColor Yellow
$deleteResp = curl -s -X DELETE "$BaseUrl/api/me/settings" `
    -H "Authorization: Bearer $token" `
    -H "X-Tenant-ID: tenant-1" `
    -w "`n%{http_code}"

$deleteStatus = $deleteResp[-1]

if ($deleteStatus -eq "204") {
    Write-Host "✓ Status $deleteStatus (No Content)" -ForegroundColor Green
} else {
    Write-Host "✗ Status $deleteStatus" -ForegroundColor Red
}

Write-Host "`n=== TESTE COMPLETO FINALIZADO ===" -ForegroundColor Cyan
