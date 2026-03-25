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

Write-Host "=== Isolated Test: Register -> Login -> List Users ===" -ForegroundColor Cyan

# 1. Register
$email = "iso$(Get-Random)@test.com"
$password = "Test@123456"

$reg = @{
    email = $email
    password = $password
    confirmPassword = $password
    name = "ISO User"
} | ConvertTo-Json

Write-Host "`n1. Register" -ForegroundColor Yellow
$regRes = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $reg `
    -UseBasicParsing

Write-Host "✅ Registered" -ForegroundColor Green

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
$decoded = Decode-Jwt $token

Write-Host "✅ Logged in" -ForegroundColor Green
Write-Host "   Email: $($decoded.sub)" -ForegroundColor Gray
Write-Host "   Role: $($decoded.role)" -ForegroundColor Gray
Write-Host "   Exp: $($decoded.exp)" -ForegroundColor Gray

# 3. List users (should 403 or 401)
Write-Host "`n3. GET /users (list all)" -ForegroundColor Yellow

try {
    $usersRes = Invoke-WebRequest "$baseUrl/users?page=0&size=10" `
        -Method GET `
        -Headers @{
            Authorization="Bearer $token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing -ErrorAction Stop # This is important!
        
    Write-Host "❌ ERROR: Should have failed!" -ForegroundColor Red
    Write-Host "Response: $($usersRes.StatusCode)" -ForegroundColor Red
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    
    if ($status -eq 403) {
        Write-Host "✅ Status: $status (Correct!)" -ForegroundColor Green
    } else {
        Write-Host "❌ Status: $status (Expected 403)" -ForegroundColor Red
    }
    
    try {
        $responseStream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($responseStream)
        $body = $reader.ReadToEnd()
        Write-Host "Body: $body" -ForegroundColor DarkGray
    } catch { }
}
