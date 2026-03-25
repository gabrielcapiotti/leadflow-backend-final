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

Write-Host "=== Debug: Sequential Requests with Token Check ===" -ForegroundColor Cyan

# 1. Register
$email = "seq$(Get-Random)@test.com"
$password = "Test@123456"

$reg = @{
    email = $email
    password = $password
    confirmPassword = $password
    name = "Seq User"
} | ConvertTo-Json

Write-Host "`n1. Register" -ForegroundColor Yellow
$regRes = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $reg `
    -UseBasicParsing

$token = ($regRes.Content | ConvertFrom-Json).accessToken
$userId = (Decode-Jwt $token).userId

Write-Host "✅ Registered, UserId: $userId" -ForegroundColor Green

# 2. Login
Write-Host "`n2. Login" -ForegroundColor Yellow
$login = @{
    email = $email
    password = $password
} | ConvertTo-Json

$loginRes = Invoke-WebRequest "$baseUrl/auth/login" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $login `
    -UseBasicParsing

$token = ($loginRes.Content | ConvertFrom-Json).accessToken
Write-Host "✅ Logged in" -ForegroundColor Green
Write-Host "   Token (from login): $($token.Substring(0, 40))..." -ForegroundColor Gray

# 3. GET own user
Write-Host "`n3. GET /users/{id}" -ForegroundColor Yellow
$getRes = Invoke-WebRequest "$baseUrl/users/$userId" `
    -Method GET `
    -Headers @{
        Authorization="Bearer $token"
        "X-Tenant-ID"=$tenantId
    } `
    -UseBasicParsing

Write-Host "✅ GET /users/{id}: $($getRes.StatusCode)" -ForegroundColor Green
Write-Host "   Token still: $($token.Substring(0, 40))..." -ForegroundColor Gray

# 4. PUT own user
Write-Host "`n4. PUT /users/{id}" -ForegroundColor Yellow
$update = @{
    name = "Updated"
    email = $email
    roleId = "00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

$putRes = Invoke-WebRequest "$baseUrl/users/$userId" `
    -Method PUT `
    -Headers @{
        Authorization="Bearer $token"
        "X-Tenant-ID"=$tenantId
        "Content-Type"="application/json"
    } `
    -Body $update `
    -UseBasicParsing

Write-Host "✅ PUT /users/{id}: $($putRes.StatusCode)" -ForegroundColor Green
Write-Host "   Token still: $($token.Substring(0, 40))..." -ForegroundColor Gray

# 5. GET /users list - WITH FRESH VERIFICATION
Write-Host "`n5. GET /users (list)" -ForegroundColor Yellow
Write-Host "   Before request:" -ForegroundColor Gray
Write-Host "   - Token exists: $(-not [string]::IsNullOrEmpty($token))" -ForegroundColor Gray
Write-Host "   - Token length: $($token.Length)" -ForegroundColor Gray
$decoded = Decode-Jwt $token
Write-Host "   - Token role: $($decoded.role)" -ForegroundColor Gray
Write-Host "   - Token sub: $($decoded.sub)" -ForegroundColor Gray

try {
    $listRes = Invoke-WebRequest "$baseUrl/users?page=0&size=10" `
        -Method GET `
        -Headers @{
            Authorization="Bearer $token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing -ErrorAction Stop
        
    Write-Host "❌ Got 200 (should have failed)" -ForegroundColor Red
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    
    if ($status -eq 403) {
        Write-Host "✅ Status 403 (Correct)" -ForegroundColor Green
    } else {
        Write-Host "❌ Status $status (Expected 403)" -ForegroundColor Red
    }
    
    try {
        $responseStream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($responseStream)
        $body = $reader.ReadToEnd()
        Write-Host "   Body: $($body.Substring(0, 80))..." -ForegroundColor DarkGray
    } catch { }
}
