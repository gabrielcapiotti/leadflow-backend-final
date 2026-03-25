#!/usr/bin/env pwsh

$tenantId = "public"
$baseUrl = "http://localhost:8081"

function Decode-Jwt {
    param([string]$token)

    $parts = $token.Split('.')
    if ($parts.Count -ne 3) { return $null }

    $payload = $parts[1]
    while ($payload.Length % 4) { $payload += "=" }

    return [System.Text.Encoding]::UTF8.GetString(
        [System.Convert]::FromBase64String($payload)
    ) | ConvertFrom-Json
}

# ========================================
# 1. REGISTER
# ========================================

$email = "debug$(Get-Random)@test.com"
$password = "Test@123456"

$reg = @{
    email = $email
    password = $password
    confirmPassword = $password
    name = "Debug User"
} | ConvertTo-Json

Write-Host "🔍 Registering user: $email"

$res = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $reg `
    -UseBasicParsing

$tokenJson = $res.Content | ConvertFrom-Json
$token = $tokenJson.accessToken

Write-Host ""
Write-Host "Token received:" -ForegroundColor Green
Write-Host $token.Substring(0, 50) + "..." -ForegroundColor Yellow
Write-Host ""

$payload = Decode-Jwt $token
Write-Host "Token Payload:" -ForegroundColor Green
$payload | ConvertTo-Json | Write-Host

# ========================================
# 2. LOGIN WITH THAT USER
# ========================================

$login = @{
    email = $email
    password = $password
} | ConvertTo-Json

Write-Host ""
Write-Host "🔍 Logging in with: $email"

$res = Invoke-WebRequest "$baseUrl/auth/login" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $login `
    -UseBasicParsing

$tokenJson = $res.Content | ConvertFrom-Json
$token = $tokenJson.accessToken

Write-Host ""
Write-Host "Token received:" -ForegroundColor Green
Write-Host $token.Substring(0, 50) + "..." -ForegroundColor Yellow
Write-Host ""

$payload = Decode-Jwt $token
Write-Host "Token Payload:" -ForegroundColor Green
$payload | ConvertTo-Json | Write-Host

$roles = $payload.authorities
Write-Host ""
Write-Host "User Roles:" -ForegroundColor Cyan
foreach ($role in $roles) {
    Write-Host "  - $role"
}

# ========================================
# 3. TRY TO ACCESS ADMIN ENDPOINT
# ========================================

Write-Host ""
Write-Host "🔍 Attempting to access admin endpoint (GET /users)..."

try {
    $res = Invoke-WebRequest "$baseUrl/users" `
        -Headers @{
            Authorization="Bearer $token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing

    Write-Host "✅ SUCCESS - Status: $($res.StatusCode)" -ForegroundColor Green
    Write-Host "Response:"
    ($res.Content | ConvertFrom-Json | ConvertTo-Json) | Write-Host
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ FAILED - Status: $status" -ForegroundColor Red

    try {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $body = $reader.ReadToEnd()
        Write-Host "Response Body:" -ForegroundColor Yellow
        $body | ConvertFrom-Json | ConvertTo-Json | Write-Host -ForegroundColor DarkGray
    } catch {
        Write-Host "Could not read response body"
    }
}
