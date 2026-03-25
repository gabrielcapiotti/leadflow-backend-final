Write-Host "TEST: Extract userId from JWT" -ForegroundColor Cyan

# Register
$body = @{
    email = "test$(Get-Random)@test.com"
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "Test User"
} | ConvertTo-Json

$resp = Invoke-WebRequest http://localhost:8081/auth/register `
    -Method POST `
    -Headers @{"X-Tenant-ID"="public";"Content-Type"="application/json"} `
    -Body $body `
    -UseBasicParsing | ConvertFrom-Json

$token = $resp.accessToken

# Decode JWT (middle part is the payload)
$parts = $token.Split('.')
$payload = $parts[1]

# Add padding if needed
$padding = 4 - ($payload.Length % 4)
if ($padding -ne 4) { $payload += "=" * $padding }

# Decode from base64
$decoded = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload)) | ConvertFrom-Json

$userId = $decoded.userId
Write-Host "userId: $userId" -ForegroundColor Green
Write-Host "role: $($decoded.role)" -ForegroundColor Green
Write-Host "email: $($decoded.sub)" -ForegroundColor Green

# Test GET
Write-Host "`nTesting GET /users/$userId" -ForegroundColor Cyan
$getResp = Invoke-WebRequest http://localhost:8081/users/$userId `
    -Method GET `
    -Headers @{Authorization="Bearer $token";"X-Tenant-ID"="public"} `
    -UseBasicParsing
Write-Host "GET Status: $($getResp.StatusCode)" -ForegroundColor Green

# Test DELETE
Write-Host "`nTesting DELETE /users/$userId" -ForegroundColor Cyan
try {
    $delResp = Invoke-WebRequest http://localhost:8081/users/$userId `
        -Method DELETE `
        -Headers @{Authorization="Bearer $token";"X-Tenant-ID"="public"} `
        -UseBasicParsing `
        -ErrorAction Stop
    Write-Host "DELETE Status: $($delResp.StatusCode)" -ForegroundColor Green
} catch {
    $code = $_.Exception.Response.StatusCode.Value__
    Write-Host "DELETE Status: $code" -ForegroundColor Red
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = [System.IO.StreamReader]::new($stream)
    $body = $reader.ReadToEnd()
    Write-Host "Response: $body" -ForegroundColor Yellow
}
