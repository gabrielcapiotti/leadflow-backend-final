function Decode-Jwt {
    param([string]$token)

    $parts = $token.Split('.')
    if ($parts.Count -ne 3) { return $null }

    $payload = $parts[1]

    # FIX Base64URL
    $payload = $payload.Replace('-', '+').Replace('_', '/')

    switch ($payload.Length % 4) {
        2 { $payload += '==' }
        3 { $payload += '=' }
    }

    try {
        return [System.Text.Encoding]::UTF8.GetString(
            [System.Convert]::FromBase64String($payload)
        ) | ConvertFrom-Json
    } catch {
        return $null
    }
}

$baseUrl = "http://localhost:8081"
$tenantId = "public"

Write-Host "================================="
Write-Host "[TEST] AUTHORIZATION DIAGNOSIS"
Write-Host "================================="
Write-Host ""

# Helper headers
function Get-Headers($token=$null) {
    $headers = @{
        "X-Tenant-Id" = $tenantId
        "Content-Type" = "application/json"
    }

    if ($token) {
        $headers["Authorization"] = "Bearer $token"
    }

    return $headers
}

# Register user1
$email1 = "test1-$(Get-Random)@test.com"
$reg1 = @{
    email=$email1
    password="Test@123456"
    confirmPassword="Test@123456"
    name="Test1"
} | ConvertTo-Json

$res1 = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers (Get-Headers) `
    -Body $reg1 `
    -UseBasicParsing

$data1 = $res1.Content | ConvertFrom-Json
$token1 = $data1.accessToken

if (-not $token1) {
    Write-Host "[FATAL] Token1 not returned"
    exit
}

$decoded1 = Decode-Jwt $token1
$user1 = $decoded1.userId

# Register user2
$email2 = "test2-$(Get-Random)@test.com"
$reg2 = @{
    email=$email2
    password="Test@123456"
    confirmPassword="Test@123456"
    name="Test2"
} | ConvertTo-Json

$res2 = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers (Get-Headers) `
    -Body $reg2 `
    -UseBasicParsing

$data2 = $res2.Content | ConvertFrom-Json
$token2 = $data2.accessToken
$user2 = (Decode-Jwt $token2).userId

Write-Host "[OK] User1: $user1"
Write-Host "[OK] User2: $user2"
Write-Host ""

# Payload
$payload = @{
    name="Updated Name"
    email="updated-$(Get-Random)@test.com"
    roleId="00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

# =========================================
# TEST 1: OWN USER (EXPECT 200)
# =========================================
Write-Host "[1] PUT own user (expect 200)..."

try {
    Invoke-WebRequest "$baseUrl/users/$user1" `
        -Method PUT `
        -Headers (Get-Headers $token1) `
        -Body $payload `
        -UseBasicParsing `
        -ErrorAction Stop

    Write-Host "[OK] 200 OK"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "[FAIL] Status $status (expected 200)"
}

Write-Host ""

# =========================================
# TEST 2: OTHER USER (EXPECT 403)
# =========================================
Write-Host "[2] PUT other user (expect 403)..."

try {
    Invoke-WebRequest "$baseUrl/users/$user2" `
        -Method PUT `
        -Headers (Get-Headers $token1) `
        -Body $payload `
        -UseBasicParsing `
        -ErrorAction Stop

    Write-Host "[CRITICAL] SECURITY BREACH (200 returned)"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__

    if ($status -eq 403) {
        Write-Host "[OK] 403 Forbidden"
    }
    elseif ($status -eq 401) {
        Write-Host "[ERROR] 401 Unauthorized (auth problem, not authorization)"
    }
    else {
        Write-Host "[FAIL] Status $status (expected 403)"
    }
}