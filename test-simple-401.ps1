#!/usr/bin/env pwsh

function Decode-Jwt {
    param([string]$token)
    $parts = $token.Split('.')
    if ($parts.Count -ne 3) { return $null }
    $payload = $parts[1]
    $payload = $payload.Replace('-', '+').Replace('_', '/')
    switch ($payload.Length % 4) {
        2 { $payload += '==' }
        3 { $payload += '=' }
    }
    return [System.Text.Encoding]::UTF8.GetString(
        [System.Convert]::FromBase64String($payload)
    ) | ConvertFrom-Json
}

$baseUrl = "http://localhost:8081"
$tenantId = "public"

# Register user1
$email1 = "testuser1-$(Get-Random)@test.com"

$body1 = @{
    email = $email1
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "User One"
} | ConvertTo-Json

$res1 = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId;"Content-Type"="application/json"} `
    -Body $body1 `
    -UseBasicParsing

$token1 = ($res1.Content | ConvertFrom-Json).accessToken
$user1 = (Decode-Jwt $token1).userId

# Register user2
$email2 = "testuser2-$(Get-Random)@test.com"

$body2 = @{
    email = $email2
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "User Two"
} | ConvertTo-Json

$res2 = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId;"Content-Type"="application/json"} `
    -Body $body2 `
    -UseBasicParsing

$user2 = (Decode-Jwt ($res2.Content | ConvertFrom-Json).accessToken).userId

Write-Host "✅ User1: $user1"
Write-Host "✅ User2: $user2"
Write-Host ""

# Try UPDATE
$payload = @{
    name = "Updated Name"
    email = "updated-$(Get-Random)@test.com"
    roleId = "00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

Write-Host "Test: User1 trying to UPDATE User2 (should be 403)"
Write-Host ""

try {
    Invoke-WebRequest "$baseUrl/users/$user2" `
        -Method PUT `
        -Headers @{
            "Authorization" = "Bearer $token1"
            "X-Tenant-ID" = $tenantId
            "Content-Type" = "application/json"
        } `
        -Body $payload `
        -UseBasicParsing -ErrorAction Stop
    
    Write-Host "❌ SECURITY ISSUE: Returned 200 (should be 403)"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    $body = $reader.ReadToEnd()
    
    Write-Host "Status: $status"
    Write-Host "Body: $body"
    
    if ($status -eq 403) {
        Write-Host "✅ CORRECT: 403 Forbidden"
    } elseif ($status -eq 401) {
        Write-Host "❌ WRONG: 401 Unauthorized (should be 403)"
    }
}
