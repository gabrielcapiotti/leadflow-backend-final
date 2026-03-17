Write-Host "Diagnosticando raiz do problema..." -ForegroundColor Cyan

$loginBody = @{ email = "carlos@leadflow.com"; password = "SenhaForte@123" } | ConvertTo-Json
$resp = Invoke-WebRequest -Uri "http://localhost:8081/auth/login" -Method POST -Headers @{"Content-Type"="application/json"} -Body $loginBody -UseBasicParsing
$data = $resp.Content | ConvertFrom-Json
$token = $data.accessToken

Write-Host "Token recebido"
Write-Host ""

# Decodificar JWT
$parts = $token.Split('.')
$payload = $parts[1]

while ($payload.Length % 4) { $payload += "=" }

$decoded = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload))
$tokenData = $decoded | ConvertFrom-Json

Write-Host "=== Dados do Token ===" -ForegroundColor Yellow
Write-Host "User ID: $($tokenData.userId)"
Write-Host "Email (sub): $($tokenData.sub)"
Write-Host "Tenant: $($tokenData.tenant)"
Write-Host ""

$userId = $tokenData.userId

Write-Host "=== Teste Admin Endpoint ===" -ForegroundColor Yellow
Write-Host "GET /api/settings/$userId"

$h = @{"Authorization"="Bearer $token";"X-Tenant-ID"="public";"Content-Type"="application/json"}

try {
    $r = Invoke-WebRequest -Uri "http://localhost:8081/api/settings/$userId" -Method GET -Headers $h -UseBasicParsing -ErrorAction Stop
    Write-Host "Status: $($r.StatusCode) - OK!" -ForegroundColor Green
    $response = $r.Content | ConvertFrom-Json
    Write-Host "Vendor: $($response.vendorName)" -ForegroundColor Cyan
} catch {
    Write-Host "ERRO - Status: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    Write-Host "Message: $($_.Exception.Message)" -ForegroundColor Red
}
