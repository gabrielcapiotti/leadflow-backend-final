Write-Host "=== TEST PUBLIC ENDPOINTS ===" -ForegroundColor Cyan

$loginBody = @{ email = "carlos@leadflow.com"; password = "SenhaForte@123" } | ConvertTo-Json
Write-Host "[1] Login..." -ForegroundColor Yellow
$loginResp = Invoke-WebRequest -Uri "http://localhost:8081/auth/login" -Method POST -Headers @{"Content-Type"="application/json"} -Body $loginBody -UseBasicParsing
$token = ($loginResp.Content | ConvertFrom-Json).accessToken

$h = @{"Authorization"="Bearer $token";"X-Tenant-ID"="public";"Content-Type"="application/json"}
$body = @{ vendorName="Public Vendor"; whatsapp="+5511987654321"; companyName="Public Co"; logo="https://example.com/logo.png"; welcomeMessage="Welcome" } | ConvertTo-Json

Write-Host "[2] Create setting..." -ForegroundColor Yellow
$r = Invoke-WebRequest -Uri "http://localhost:8081/api/me/settings" -Method PUT -Headers $h -Body $body -UseBasicParsing
$id = ($r.Content | ConvertFrom-Json).id
Write-Host "Created: $id" -ForegroundColor Green

Write-Host "[3] GET public endpoint (no auth)..." -ForegroundColor Yellow
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/api/public/settings/$id" -Method GET -UseBasicParsing
    Write-Host "Status 200 OK" -ForegroundColor Green
    $data = $r.Content | ConvertFrom-Json
    Write-Host "Vendor: $($data.vendorName)" -ForegroundColor Green
} catch {
    Write-Host "FAIL: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

Write-Host "[4] GET public with invalid ID..." -ForegroundColor Yellow
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/api/public/settings/00000000-0000-0000-0000-000000000000" -Method GET -UseBasicParsing
} catch {
    Write-Host "Status $($_.Exception.Response.StatusCode) (expected)" -ForegroundColor Yellow
}

Write-Host "`nPublic endpoints test complete!" -ForegroundColor Green
