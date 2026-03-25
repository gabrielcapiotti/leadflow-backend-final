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

Write-Host "=== DELETE USER TEST (DEBUG) ===" -ForegroundColor Cyan

# 1. REGISTER
Write-Host "`n1. Creating user..." -ForegroundColor Yellow
$email = "del_test_$(Get-Random)@test.com"
$reg = @{
    email = $email
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "Delete Test User"
} | ConvertTo-Json

$regRes = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $reg `
    -UseBasicParsing

$token = ($regRes.Content | ConvertFrom-Json).accessToken
$userId = (Decode-Jwt $token).userId

Write-Host "   UserId: $userId"
Write-Host "   Token: $($token.Substring(0,50))..." -ForegroundColor DarkGray

# 2. LOGIN (Fresh token)
Write-Host "`n2. Login again (fresh token)..." -ForegroundColor Yellow
$login = @{
    email = $email
    password = "Test@123456"
} | ConvertTo-Json

$loginRes = Invoke-WebRequest "$baseUrl/auth/login" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $login `
    -UseBasicParsing

$token = ($loginRes.Content | ConvertFrom-Json).accessToken
$tokenDecoded = Decode-Jwt $token

Write-Host "   Email (sub): $($tokenDecoded.sub)" -ForegroundColor DarkGray
Write-Host "   Role: $($tokenDecoded.role)" -ForegroundColor DarkGray
Write-Host "   Token: $($token.Substring(0,50))..." -ForegroundColor DarkGray

# 3. GET /users (should 403)
Write-Host "`n3. GET /users (list - should 403)..." -ForegroundColor Yellow
try {
    $res = Invoke-WebRequest "$baseUrl/users?page=0&size=10" `
        -Method GET `
        -Headers @{
            Authorization="Bearer $token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing -ErrorAction Stop
    Write-Host "   ❌ Should have been rejected!" -ForegroundColor Red
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    if ($status -eq 403) {
        Write-Host "   ✅ 403 Forbidden (correct)" -ForegroundColor Green
    } else {
        Write-Host "   ❌ $status (expected 403)" -ForegroundColor Red
        $body = $_.Exception.Response.GetResponseStream() | % { $r = New-Object System.IO.StreamReader($_); $r.ReadToEnd() }
        Write-Host "   Response: $body" -ForegroundColor DarkGray
    }
}

# 4. GET /users/{id} (own user - should 200)
Write-Host "`n4. GET /users/{$userId} (self - should 200)..." -ForegroundColor Yellow
try {
    $res = Invoke-WebRequest "$baseUrl/users/$userId" `
        -Method GET `
        -Headers @{
            Authorization="Bearer $token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing -ErrorAction Stop
    
    if ($res.StatusCode -eq 200) {
        Write-Host "   ✅ 200 OK" -ForegroundColor Green
    } else {
        Write-Host "   ⚠️ $($res.StatusCode)" -ForegroundColor Yellow
    }
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "   ❌ $status" -ForegroundColor Red
    $body = $_.Exception.Response.GetResponseStream() | % { $r = New-Object System.IO.StreamReader($_); $r.ReadToEnd() }
    Write-Host "   Response: $body" -ForegroundColor DarkGray
}

# 5. DELETE /users/{id} (soft delete - should 204)
Write-Host "`n5. DELETE /users/{$userId} (self - should 204)..." -ForegroundColor Yellow
try {
    $res = Invoke-WebRequest "$baseUrl/users/$userId" `
        -Method DELETE `
        -Headers @{
            Authorization="Bearer $token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing -ErrorAction Stop
    
    if ($res.StatusCode -eq 204) {
        Write-Host "   ✅ 204 No Content" -ForegroundColor Green
    } else {
        Write-Host "   ⚠️ $($res.StatusCode)" -ForegroundColor Yellow
    }
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "   ❌ $status (expected 204)" -ForegroundColor Red
    $body = $_.Exception.Response.GetResponseStream() | % { $r = New-Object System.IO.StreamReader($_); $r.ReadToEnd() }
    Write-Host "   Response: $body" -ForegroundColor DarkGray
}

# 6. Verify deleted
Write-Host "`n6. GET /users/{$userId} (after delete - should 404)..." -ForegroundColor Yellow
try {
    $res = Invoke-WebRequest "$baseUrl/users/$userId" `
        -Method GET `
        -Headers @{
            Authorization="Bearer $token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing -ErrorAction Stop
    Write-Host "   ❌ Should have been deleted!" -ForegroundColor Red
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    if ($status -eq 404) {
        Write-Host "   ✅ 404 Not Found (correct)" -ForegroundColor Green
    } else {
        Write-Host "   ❌ $status (expected 404)" -ForegroundColor Red
        $body = $_.Exception.Response.GetResponseStream() | % { $r = New-Object System.IO.StreamReader($_); $r.ReadToEnd() }
        Write-Host "   Response: $body" -ForegroundColor DarkGray
    }
}

Write-Host "`n=== TEST COMPLETE ===" -ForegroundColor Cyan
