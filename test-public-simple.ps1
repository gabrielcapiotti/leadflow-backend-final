Write-Host "=== TEST PUBLIC ENDPOINTS (simplified paths) ===" -ForegroundColor Cyan

# 1. Get token
$loginBody = @{ email = "carlos@leadflow.com"; password = "SenhaForte@123" } | ConvertTo-Json
$loginResp = Invoke-WebRequest -Uri "http://localhost:8081/auth/login" -Method POST -Headers @{"Content-Type"="application/json"} -Body $loginBody -UseBasicParsing
$token = ($loginResp.Content | ConvertFrom-Json).accessToken

# 2. Create setting
$h = @{"Authorization"="Bearer $token";"X-Tenant-ID"="public";"Content-Type"="application/json"}
$body = @{ vendorName="Public Test"; whatsapp="+5511987654321"; companyName="Public Co" } | ConvertTo-Json
$r = Invoke-WebRequest -Uri "http://localhost:8081/api/me/settings" -Method PUT -Headers $h -Body $body -UseBasicParsing
$id = ($r.Content | ConvertFrom-Json).id
Write-Host "Created: $id" -ForegroundColor Green

# 3. Test /public/settings/{id} WITHOUT auth
Write-Host "`nTesting /public/settings/$id (NO auth)" -ForegroundColor Yellow
try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/public/settings/$id" -Method GET -UseBasicParsing
    Write-Host "Status: $($r.StatusCode) OK" -ForegroundColor Green
    $data = $r.Content | ConvertFrom-Json
    Write-Host "Vendor: $($data.vendorName)" -ForegroundColor Green
    Write-Host "Company: $($data.companyName)" -ForegroundColor Green
} catch {
    Write-Host "FAIL: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
}

Write-Host "`nTest complete!" -ForegroundColor Green
