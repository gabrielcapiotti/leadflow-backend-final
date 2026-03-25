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

# Register
$email = "debug$(Get-Random)@test.com"
$password = "Test@123456"

$reg = @{
    email = $email
    password = $password
    confirmPassword = $password
    name = "Debug User"
} | ConvertTo-Json

Write-Host "Register: $email"
$res = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $reg `
    -UseBasicParsing

$tokenJson = $res.Content | ConvertFrom-Json
$token = $tokenJson.accessToken

Write-Host "Token payload:"
$payload = Decode-Jwt $token
$payload | ConvertTo-Json | Write-Host

# Get current user info
Write-Host "`nFetching user info..."
$meRes = Invoke-WebRequest "$baseUrl/auth/me" `
    -Headers @{
        Authorization="Bearer $token"
        "X-Tenant-ID"=$tenantId
    } `
    -UseBasicParsing

Write-Host "User info:"
($meRes.Content | ConvertFrom-Json) | ConvertTo-Json | Write-Host

# Try admin endpoint
Write-Host "`nTrying admin endpoint (GET /users)..."
try {
    Invoke-WebRequest "$baseUrl/users" `
        -Headers @{
            Authorization="Bearer $token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing | Out-Null
    Write-Host "SUCCESS"
} catch {
    Write-Host "FAILED: $($_.Exception.Response.StatusCode.Value__)"
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    $body = $reader.ReadToEnd()
    Write-Host "Response:"
    Write-Host $body
}
