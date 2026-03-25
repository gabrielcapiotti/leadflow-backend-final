Write-Host "COMPARISON TEST" -ForegroundColor Cyan

# Test 1: Simple token
$resp1 = @{
    email = "test$(Get-Random)@test.com"
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "Test User"
} | ConvertTo-Json | Invoke-WebRequest http://localhost:8081/auth/register `
    -Method POST `
    -Headers @{"X-Tenant-ID"="public";"Content-Type"="application/json"} `
    -UseBasicParsing | ConvertFrom-Json

$token1 = $resp1.accessToken
$parts = $token1.Split('.')
$payload = $parts[1]
$padding = 4 - ($payload.Length % 4)
if ($padding -ne 4) { $payload += "=" * $padding }
$decoded1 = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload)) | ConvertFrom-Json

$userId1 = $decoded1.userId
Write-Host "Test 1: Simple token from register" -ForegroundColor Green
Write-Host "  Token from: register only" -ForegroundColor Gray
Write-Host "  DELETE result:" -ForegroundColor Gray -NoNewline
try {
    $r1 = Invoke-WebRequest http://localhost:8081/users/$userId1 -Method DELETE `
        -Headers @{Authorization="Bearer $token1";"X-Tenant-ID"="public"} `
        -UseBasicParsing -ErrorAction Stop
    Write-Host " $($r1.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host " $($_.Exception.Response.StatusCode.Value__)" -ForegroundColor Red
}

# Test 2: Token from LOGIN after multiple operations
$email2 = "test$(Get-Random)@test.com"
$pass2 = "Test@123456"

# Register
$resp2 = @{
    email = $email2
    password = $pass2
    confirmPassword = $pass2
    name = "Test User"
} | ConvertTo-Json | Invoke-WebRequest http://localhost:8081/auth/register `
    -Method POST `
    -Headers @{"X-Tenant-ID"="public";"Content-Type"="application/json"} `
    -UseBasicParsing | ConvertFrom-Json

$token2a = $resp2.accessToken
$parts = $token2a.Split('.')
$payload = $parts[1]
$padding = 4 - ($payload.Length % 4)
if ($padding -ne 4) { $payload += "=" * $padding }
$decoded2a = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload)) | ConvertFrom-Json
$userId2 = $decoded2a.userId

# Login (get new token)
$login = @{email=$email2; password=$pass2} | ConvertTo-Json
$resp2b = Invoke-WebRequest http://localhost:8081/auth/login `
    -Method POST `
    -Headers @{"X-Tenant-ID"="public";"Content-Type"="application/json"} `
    -Body $login `
    -UseBasicParsing | ConvertFrom-Json

$token2b = $resp2b.accessToken

# GET (verify auth works)
$r2g = Invoke-WebRequest http://localhost:8081/users/$userId2 `
    -Headers @{Authorization="Bearer $token2b";"X-Tenant-ID"="public"} `
    -UseBasicParsing -ErrorAction SilentlyContinue

# PUT (verify auth still works)
$update = @{name="X";email="newemail$(Get-Random)@test.com";roleId="00000000-0000-0000-0000-000000000001"} | ConvertTo-Json
$r2p = Invoke-WebRequest http://localhost:8081/users/$userId2 `
    -Method PUT `
    -Headers @{Authorization="Bearer $token2b";"X-Tenant-ID"="public";"Content-Type"="application/json"} `
    -Body $update `
    -UseBasicParsing -ErrorAction SilentlyContinue

# DELETE
$r2d = Invoke-WebRequest http://localhost:8081/users/$userId2 `
    -Method DELETE `
    -Headers @{Authorization="Bearer $token2b";"X-Tenant-ID"="public"} `
    -UseBasicParsing -ErrorAction SilentlyContinue

Write-Host "Test 2: Token from LOGIN after GET+PUT" -ForegroundColor Green
Write-Host "  Token from: login (after register)" -ForegroundColor Gray
Write-Host "  GET result: $($r2g.StatusCode)" -ForegroundColor Gray
Write-Host "  PUT result: $($r2p.StatusCode)" -ForegroundColor Gray
Write-Host "  DELETE result:" -ForegroundColor Gray -NoNewline
Write-Host " $($r2d.StatusCode)" -ForegroundColor Green
