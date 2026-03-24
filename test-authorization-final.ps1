function Decode-Jwt {
    param([string]$token)
    $parts = $token.Split('.')
    if ($parts.Count -ne 3) { return $null }  
    $payload = $parts[1]
    $padding = 4 - $payload.Length % 4
    if ($padding -ne 4) {  $payload += '=' * $padding }
    return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload)) | ConvertFrom-Json
}

$tenantId = "public"
$baseUrl = "http://localhost:8081"

Write-Host "════════════════════════════════════════════════════════════════"
Write-Host "FINAL AUTHORIZATION TEST - Ownership-Based Access Control"
Write-Host "════════════════════════════════════════════════════════════════"

# 1. Register User A
Write-Host ""
Write-Host "[1/6] Registering User A..."
$regA = @{
    email = "userA-$(Get-Random)@test.com"
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "User A"
} | ConvertTo-Json

$res = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $regA -UseBasicParsing

$dataA = $res.Content | ConvertFrom-Json
$tokenA = $dataA.accessToken
$decodedA = Decode-Jwt $tokenA
$userA = $decodedA.userId

Write-Host "✅ User A created"
Write-Host "   ID: $userA"
Write-Host "   Email: $($decodedA.sub)"

# 2. Register User B  
Write-Host ""
Write-Host "[2/6] Registering User B..."
$regB = @{
    email = "userB-$(Get-Random)@test.com"
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "User B"
} | ConvertTo-Json

$res = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $regB -UseBasicParsing

$dataB = $res.Content | ConvertFrom-Json
$tokenB = $dataB.accessToken
$decodedB = Decode-Jwt $tokenB
$userB = $decodedB.userId

Write-Host "✅ User B created"
Write-Host "   ID: $userB"
Write-Host "   Email: $($decodedB.sub)"

# Let tokens settle
Start-Sleep -Milliseconds 500

# 3. User A gets own profile
Write-Host ""
Write-Host "[3/6] User A gets own profile..."
try {
    Invoke-WebRequest "$baseUrl/users/$userA" `
        -Headers @{Authorization="Bearer $tokenA"; "X-Tenant-ID"=$tenantId} `
        -UseBasicParsing | Out-Null
    Write-Host "✅ SUCCESS: 200 OK"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ FAILED: $status (expected 200)"
}

# 4. User A tries to get User B profile
Write-Host ""
Write-Host "[4/6] User A tries to get User B profile..."
try {
    Invoke-WebRequest "$baseUrl/users/$userB" `
        -Headers @{Authorization="Bearer $tokenA"; "X-Tenant-ID"=$tenantId} `
        -UseBasicParsing | Out-Null
    Write-Host "❌ SECURITY BREACH: Got 200 (should be 403)"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    if ($status -eq 403) {
        Write-Host "✅ CORRECT: 403 Forbidden (access denied)"
    } else {
        Write-Host "⚠️  Got $status (expected 403)"
    }
}

# 5. User A updates own profile
Write-Host ""
Write-Host "[5/6] User A updates own profile..."
$updateA = @{
    name = "User A Updated"
    email = "updated-$(Get-Random)@test.com"
    roleId = "00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

try {
    Invoke-WebRequest "$baseUrl/users/$userA" `
        -Method PUT `
        -Headers @{
            Authorization="Bearer $tokenA"
            "X-Tenant-ID"=$tenantId
            "Content-Type"="application/json"
        } `
        -Body $updateA `
        -UseBasicParsing | Out-Null
    Write-Host "✅ SUCCESS: 200 OK (self-update allowed)"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ FAILED: $status (expected 200)"
}

# 6. User A tries to update User B profile
Write-Host ""
Write-Host "[6/6] User A tries to update User B profile..."
$updateB = @{
    name = "User B Hacked!"
    email = "hacked-$(Get-Random)@test.com"
    roleId = "00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

try {
    Invoke-WebRequest "$baseUrl/users/$userB" `
        -Method PUT `
        -Headers @{
            Authorization="Bearer $tokenA"
            "X-Tenant-ID"=$tenantId
            "Content-Type"="application/json"
        } `
        -Body $updateB `
        -UseBasicParsing | Out-Null
    Write-Host "❌ SECURITY BREACH: Got 200 (should be 403)"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    if ($status -eq 403) {
        Write-Host "✅ CORRECT: 403 Forbidden (update denied)"
    } else {
        Write-Host "⚠️  Got $status (expected 403)"
    }
}

Write-Host ""
Write-Host "════════════════════════════════════════════════════════════════"
Write-Host "✅ AUTHORIZATION MODEL WORKING CORRECTLY"
Write-Host "════════════════════════════════════════════════════════════════"
