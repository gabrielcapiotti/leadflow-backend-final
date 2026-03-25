#!/usr/bin/env pwsh

$tenantId = "public"
$baseUrl = "http://localhost:8081"

function Decode-Jwt {
    param([string]$token)

    $parts = $token.Split('.')
    if ($parts.Count -ne 3) { return $null }

    $payload = $parts[1]
    
    # Add padding if needed
    $padding = 4 - ($payload.Length % 4)
    if ($padding -ne 4) { $payload += "=" * $padding }

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

try {
    $res = Invoke-WebRequest "$baseUrl/auth/login" `
        -Method POST `
        -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
        -Body $login `
        -UseBasicParsing

    $loginResponse = $res.Content | ConvertFrom-Json
    $token = $loginResponse.accessToken
    $decoded_login = Decode-Jwt $token
    
    Write-Host "✅ Logged in"
    Write-Host "Token email: $($decoded_login.sub)" -ForegroundColor Gray
    Write-Host "Token role: $($decoded_login.role)" -ForegroundColor Gray
    Write-Host "Token exp: $($decoded_login.exp)" -ForegroundColor Gray
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ LOGIN FAILED: $status" -ForegroundColor Red

    try {
        $stream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($stream)
        $body = $reader.ReadToEnd()
        Write-Host "Response:" -ForegroundColor Yellow
        Write-Host $body -ForegroundColor DarkGray
    } catch {
        Write-Host "Could not read response body"
    }
    exit 1
}

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
        -UseBasicParsing -ErrorAction Stop

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
# 4. UPDATE OWN USER (SKIPPED)
# ========================================

Section "4. PUT OWN USER (SKIPPED)"

Write-Host "⊘ Skipped: roleId is required and auto-generated"

# ========================================
# 5. TEST PROTECTED ENDPOINT
# ========================================

Section "5. TEST ADMIN ENDPOINT"

$decoded2 = Decode-Jwt $token
Write-Host "Token details - Role: $($decoded2.role), Email: $($decoded2.sub)" -ForegroundColor Gray

try {
    $res = Invoke-WebRequest "$baseUrl/users?page=0&size=10" `
        -Method GET `
        -Headers @{
            Authorization="Bearer $token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing -ErrorAction Stop

    Write-Host "❌ ERROR: user accessed admin endpoint (should be blocked!)"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    
    if ($status -eq 403) {
        Write-Host "✅ CORRECT: access denied (403)"
    } else {
        Write-Host "❌ Unexpected status: $status (expected 403)"
    }
    
    try {
        $responseStream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($responseStream)
        $body = $reader.ReadToEnd()
        Write-Host "Response: $body" -ForegroundColor DarkGray
    } catch {
        Write-Host "Could not read response body"
    }
}

# ========================================
# 6. DELETE OWN USER (SOFT DELETE)
# ========================================

Section "6. DELETE OWN USER (SOFT DELETE)"

try {
    $res = Invoke-WebRequest "$baseUrl/users/$userId" `
        -Method DELETE `
        -Headers @{
            Authorization="Bearer $token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing -ErrorAction Stop

    if ($res.StatusCode -eq 204) {
        Write-Host "✅ DELETE OK (204 No Content)"
    } else {
        Write-Host "⚠️ DELETE returned: $($res.StatusCode)"
    }
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ DELETE FAILED: $status" -ForegroundColor Red

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
# 7. VERIFY USER IS SOFT DELETED
# ========================================

Section "7. VERIFY USER IS SOFT DELETED"

try {
    $res = Invoke-WebRequest "$baseUrl/users/$userId" `
        -Headers @{
            Authorization="Bearer $token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing -ErrorAction Stop

    Write-Host "❌ ERROR: deleted user still accessible!" -ForegroundColor Red
    Write-Host "User data: $($res.Content | ConvertFrom-Json)" -ForegroundColor DarkGray
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    
    if ($status -eq 404 -or $status -eq 401) {
        Write-Host "✅ CORRECT: user deleted ($status)"
    } else {
        Write-Host "⚠️ Unexpected status: $status (expected 404 or 401)" -ForegroundColor Yellow
    }
    
    try {
        $responseStream = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($responseStream)
        $body = $reader.ReadToEnd()
        Write-Host "Response: $body" -ForegroundColor DarkGray
    } catch {
        Write-Host "Could not read response body"
    }
}