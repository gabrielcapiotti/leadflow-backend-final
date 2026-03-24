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
$psqlHost = "localhost"
$psqlUser = "postgres"
$psqlPassword = "postgres"

# ========================================
# 1. REGISTER USER
# ========================================

Section "1. REGISTER NORMAL USER"

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

Write-Host "✅ User registered"
Write-Host "   UserId: $userId"
Write-Host "   Role: $($decoded.role)"

# ========================================
# 2. TEST /users/{id} AS NORMAL USER
# ========================================

Section "2. TEST GET /users/{id} (requires ADMIN)"

try {
    $res = Invoke-WebRequest "$baseUrl/users/$userId" `
        -Headers @{Authorization="Bearer $token"; "X-Tenant-ID"=$tenantId} `
        -UseBasicParsing
    Write-Host "❌ ERROR: Normal user accessed admin endpoint!"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    if ($status -eq 403) {
        Write-Host "✅ CORRECT: Access denied (403) - requires ADMIN role"
    } else {
        Write-Host "❌ Unexpected status: $status"
    }
}

# ========================================
# 3. TEST /auth/me AS NORMAL USER
# ========================================

Section "3. TEST GET /auth/me (public endpoint)"

try {
    $res = Invoke-WebRequest "$baseUrl/auth/me" `
        -Headers @{Authorization="Bearer $token"; "X-Tenant-ID"=$tenantId} `
        -UseBasicParsing
    Write-Host "✅ Can access /auth/me"
    $me = $res.Content | ConvertFrom-Json
    Write-Host "   Email: $($me.email)"
    Write-Host "   Role: $($me.role)"
} catch {
    Write-Host "❌ /auth/me failed"
}

# ========================================
# 4. PROMOTE USER TO ADMIN
# ========================================

Section "4. PROMOTE USER TO ADMIN"

$env:PGPASSWORD = $psqlPassword
$promoteQuery = @"
UPDATE users 
SET role_id = (SELECT id FROM roles WHERE name = 'ROLE_ADMIN' LIMIT 1)
WHERE id = '$userId'::uuid AND tenant_id = '$tenantId'
"@

& psql -h $psqlHost -U $psqlUser -d leadflow -c $promoteQuery 2>&1 | Out-Null
Write-Host "✅ User promoted to ROLE_ADMIN in database"

# Verify promotion
$verifyQuery = @"
SELECT u.id, u.email, r.name as role 
FROM users u
JOIN roles r ON u.role_id = r.id
WHERE u.id = '$userId'::uuid
"@

Write-Host ""
Write-Host "Verifying promotion in database:"
$verification = & psql -h $psqlHost -U $psqlUser -d leadflow -c $verifyQuery 2>&1
Write-Host $verification

# ========================================
# 5. LOGIN AGAIN TO GET NEW ADMIN TOKEN
# ========================================

Section "5. RE-LOGIN TO GET ADMIN TOKEN"

$login = @{email=$email; password=$password} | ConvertTo-Json

$res = Invoke-WebRequest "$baseUrl/auth/login" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $login `
    -UseBasicParsing

$token = ($res.Content | ConvertFrom-Json).accessToken
$decoded = Decode-Jwt $token
Write-Host "✅ Re-logged in with ADMIN token"
Write-Host "   Role: $($decoded.role)"

# ========================================
# 6. TEST PUT AS ADMIN
# ========================================

Section "6. TEST PUT /users/{id} (as ADMIN)"

$newEmail = "updated$(Get-Random)@test.com"
$newName = "Updated Admin User"

$update = @{
    name = $newName
    email = $newEmail
    roleId = "00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

Write-Host "Updating:"
Write-Host "  UserId: $userId"
Write-Host "  New name: $newName"
Write-Host "  New email: $newEmail"
Write-Host ""

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
    
    Write-Host "✅✅✅ PUT SUCCESS! ($($res.StatusCode))"
    Write-Host ""
    $updated = $res.Content | ConvertFrom-Json
    Write-Host "Response:"
    Write-Host ($res.Content | ConvertFrom-Json | ConvertTo-Json -Depth 2)
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ PUT FAILED: $status"
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    $error = $reader.ReadToEnd()
    Write-Host "Response: $error"
}
