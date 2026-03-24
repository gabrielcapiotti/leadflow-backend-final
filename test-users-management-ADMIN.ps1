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

function Section($t) {
    Write-Host ""
    Write-Host "═══════════════════════════════════════════"
    Write-Host $t
    Write-Host "═══════════════════════════════════════════"
}

# ========================================
# 1. REGISTER USER
# ========================================

Section "1. REGISTER USER"

$email = "user$(Get-Random)@test.com"
$password = "Test@123456"

$reg = @{
    email = $email
    password = $password
    confirmPassword = $password
    name = "Normal User"
} | ConvertTo-Json

$res = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $reg `
    -UseBasicParsing

$token = ($res.Content | ConvertFrom-Json).accessToken
$decoded = Decode-Jwt $token
$userId = $decoded.userId

Write-Host "✅ User created"
Write-Host "UserId: $userId"
Write-Host "Role: $($decoded.role)"

# ========================================
# 2. LOGIN
# ========================================

Section "2. LOGIN"

$login = @{
    email = $email
    password = $password
} | ConvertTo-Json

$res = Invoke-WebRequest "$baseUrl/auth/login" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $login `
    -UseBasicParsing

$token = ($res.Content | ConvertFrom-Json).accessToken

Write-Host "✅ Logged in"

# ========================================
# 3. GET OWN USER
# ========================================

Section "3. GET OWN USER"

try {
    $res = Invoke-WebRequest "$baseUrl/users/$userId" `
        -Headers @{
            Authorization="Bearer $token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing

    Write-Host "✅ GET OK ($($res.StatusCode))"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ GET FAILED: $status" -ForegroundColor Red

    try {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $body = $reader.ReadToEnd()
        Write-Host "Response:" -ForegroundColor Yellow
        Write-Host $body -ForegroundColor DarkGray
    } catch {
        Write-Host "Could not read response body"
    }
}

# ========================================
# 4. UPDATE OWN USER
# ========================================

Section "4. PUT OWN USER"

$newEmail = "updated$(Get-Random)@test.com"

$update = @{
    name = "Updated User"
    email = $newEmail
    roleId = "00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

try {
    $res = Invoke-WebRequest "$baseUrl/users/$userId" `
        -Method PUT `
        -Headers @{
            Authorization="Bearer $token"
            "X-Tenant-ID"=$tenantId
            "Content-Type"="application/json"
        } `
        -Body $update `
        -UseBasicParsing

    Write-Host "✅ PUT OK ($($res.StatusCode))"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ PUT FAILED: $status" -ForegroundColor Red

    try {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $body = $reader.ReadToEnd()
        Write-Host "Response:" -ForegroundColor Yellow
        Write-Host $body -ForegroundColor DarkGray
    } catch {
        Write-Host "Could not read response body"
    }
}

# ========================================
# 5. TEST PROTECTED ENDPOINT
# ========================================

Section "5. TEST ADMIN ENDPOINT"

try {
    Invoke-WebRequest "$baseUrl/users?page=0&size=10" `
        -Headers @{
            Authorization="Bearer $token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing

    Write-Host "❌ ERROR: user accessed admin endpoint (should be blocked!)"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    if ($status -eq 403) {
        Write-Host "✅ CORRECT: access denied (403)"
    } else {
        Write-Host "❌ Unexpected status: $status"
        
        try {
            $stream = $_.Exception.Response.GetResponseStream()
            $reader = New-Object System.IO.StreamReader($stream)
            $body = $reader.ReadToEnd()
            Write-Host "Response:" -ForegroundColor Yellow
            Write-Host $body -ForegroundColor DarkGray
        } catch {
            Write-Host "Could not read response body"
        }
    }
}