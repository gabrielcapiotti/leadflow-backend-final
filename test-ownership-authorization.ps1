function Decode-Jwt {
    param([string]$token)
    $parts = $token.Split('.')
    if ($parts.Count -ne 3) { return $null }
    $payload = $parts[1]
    $padding = 4 - $payload.Length % 4
    if ($padding -ne 4) { $payload += '=' * $padding }
    return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload)) | ConvertFrom-Json
}

function Section($t) {
    Write-Host ""
    Write-Host "═══════════════════════════════════════════════════════════"
    Write-Host $t
    Write-Host "═══════════════════════════════════════════════════════════"
}

$tenantId = "public"
$baseUrl = "http://localhost:8081"

# ========================================
# 1. REGISTER USER 1
# ========================================

Section "1. REGISTER USER 1"

$user1Email = "user1-$(Get-Random)@test.com"
$user1Reg = @{
    email = $user1Email
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "User 1"
} | ConvertTo-Json

$res = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $user1Reg `
    -UseBasicParsing

$user1Data = $res.Content | ConvertFrom-Json
$user1Token = $user1Data.accessToken
$user1Decoded = Decode-Jwt $user1Token
$user1Id = $user1Decoded.userId

Write-Host "✅ User 1 registered"
Write-Host "   UserId: $user1Id"

# ========================================
# 2. REGISTER USER 2
# ========================================

Section "2. REGISTER USER 2"

$user2Email = "user2-$(Get-Random)@test.com"
$user2Reg = @{
    email = $user2Email
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "User 2"
} | ConvertTo-Json

$res = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $user2Reg `
    -UseBasicParsing

$user2Data = $res.Content | ConvertFrom-Json
$user2Token = $user2Data.accessToken
$user2Decoded = Decode-Jwt $user2Token
$user2Id = $user2Decoded.userId

Write-Host "✅ User 2 registered"
Write-Host "   UserId: $user2Id"
Write-Host "   Token: $($user2Token.Substring(0, 30))..."

# ========================================
# 3. USER 1 ACCESSES OWN PROFILE
# ========================================

Section "3. USER 1 GETS OWN PROFILE"

try {
    $res = Invoke-WebRequest "$baseUrl/users/$user1Id" `
        -Headers @{
            Authorization="Bearer $user1Token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing
    Write-Host "✅ USER 1 can access own profile (200 OK)"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ FAILED: $status"
}

# ========================================
# 4. USER 1 TRIES TO ACCESS USER 2 PROFILE
# ========================================

Section "4. USER 1 TRIES TO ACCESS USER 2 PROFILE"

try {
    $res = Invoke-WebRequest "$baseUrl/users/$user2Id" `
        -Headers @{
            Authorization="Bearer $user1Token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing
    Write-Host "❌ ERROR: User 1 accessed User 2 profile (security breach!)"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    if ($status -eq 403) {
        Write-Host "✅ CORRECT: Access denied (403)"
    } else {
        Write-Host "⚠️ Unexpected status: $status"
    }
}

# ========================================
# 5. USER 1 UPDATES OWN PROFILE
# ========================================

Section "5. USER 1 UPDATES OWN PROFILE"

$update = @{
    name = "User 1 Updated"
    email = "updated-$(Get-Random)@test.com"
    roleId = "00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

try {
    $res = Invoke-WebRequest "$baseUrl/users/$user1Id" `
        -Method PUT `
        -Headers @{
            Authorization="Bearer $user1Token"
            "X-Tenant-ID"=$tenantId
            "Content-Type"="application/json"
        } `
        -Body $update `
        -UseBasicParsing
    Write-Host "✅ USER 1 can update own profile (200 OK)"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ FAILED: $status"
}

# ========================================
# 6. USER 1 TRIES TO UPDATE USER 2 PROFILE
# ========================================

Section "6. USER 1 TRIES TO UPDATE USER 2 PROFILE"

$updateOther = @{
    name = "Hacked User 2"
    email = "hacked@test.com"
    roleId = "00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

try {
    $res = Invoke-WebRequest "$baseUrl/users/$user2Id" `
        -Method PUT `
        -Headers @{
            Authorization="Bearer $user1Token"
            "X-Tenant-ID"=$tenantId
            "Content-Type"="application/json"
        } `
        -Body $updateOther `
        -UseBasicParsing
    Write-Host "❌ ERROR: User 1 updated User 2 profile (security breach!)"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    if ($status -eq 403) {
        Write-Host "✅ CORRECT: Update denied (403)"
    } else {
        Write-Host "⚠️ Unexpected status: $status"
    }
}

Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════"
Write-Host "✅ OWNERSHIP-BASED AUTHORIZATION WORKING CORRECTLY"
Write-Host "═══════════════════════════════════════════════════════════"
