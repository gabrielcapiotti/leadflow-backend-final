function Decode-Jwt {
    param([string]$token)
    $parts = $token.Split('.')
    if ($parts.Count -ne 3) { return $null }
    $payload = $parts[1]
    $padding = 4 - $payload.Length % 4
    if ($padding -ne 4) { $payload += '=' * $padding }
    return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload)) | ConvertFrom-Json
}

$tenantId = "public"
$baseUrl = "http://localhost:8081"
$psqlHost = "localhost"
$psqlPort = "5432"
$psqlUser = "postgres"
$psqlPassword = "postgres"

# Step 1: Register admin user via API
Write-Host "═══════════════════════════════════════════════════════════"
Write-Host "1. Register ADMIN user"
Write-Host "═══════════════════════════════════════════════════════════"

$adminEmail = "admin$(Get-Random)@test.com"
$adminPassword = "Test@123456"
$adminReg = @{
    email = $adminEmail
    password = $adminPassword
    confirmPassword = $adminPassword
    name = "Admin User"
} | ConvertTo-Json

try {
    $res = Invoke-WebRequest "$baseUrl/auth/register" -Method Post -ContentType "application/json" `
        -Headers @{"X-Tenant-ID"=$tenantId} -Body $adminReg -UseBasicParsing
    $adminRegData = $res.Content | ConvertFrom-Json
    $adminToken = $adminRegData.accessToken
    $decoded = Decode-Jwt -token $adminToken
    $adminId = $decoded.userId
    Write-Host "✅ Admin registered: $adminEmail"
    Write-Host "   UserId: $adminId"
    Write-Host "   Current role: $($decoded.role)"
} catch {
    Write-Host "❌ Admin registration failed"
    exit 1
}

# Step 1b: Promote admin to ROLE_ADMIN in database
Write-Host ""
Write-Host "Promoting to ROLE_ADMIN in database..."
$env:PGPASSWORD = $psqlPassword
$promoteAdminQuery = @"
UPDATE users 
SET role_id = (SELECT id FROM roles WHERE name = 'ROLE_ADMIN' LIMIT 1)
WHERE id = '$adminId'::uuid AND tenant_id = '$tenantId'
"@
& psql -h $psqlHost -p $psqlPort -U $psqlUser -d leadflow -c $promoteAdminQuery 2>&1 | Out-Null
Write-Host "✅ Admin promoted to ROLE_ADMIN"

# Step 2: Login as ADMIN to get fresh token
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════"
Write-Host "2. Re-login as ADMIN to get ROLE_ADMIN token"
Write-Host "═══════════════════════════════════════════════════════════"

$login = @{email=$adminEmail; password="Test@123456"} | ConvertTo-Json
try {
    $res = Invoke-WebRequest "$baseUrl/auth/login" -Method Post -ContentType "application/json" `
        -Headers @{"X-Tenant-ID"=$tenantId} -Body $login -UseBasicParsing
    $loginData = $res.Content | ConvertFrom-Json
    $adminToken = $loginData.accessToken
    $decoded = Decode-Jwt -token $adminToken
    Write-Host "✅ Logged in as ADMIN with fresh token"
    Write-Host "   Email: $adminEmail"
    Write-Host "   Role: $($decoded.role)"
} catch {
    Write-Host "❌ Login failed"
    exit 1
}

# Step 3: Register a regular USER
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════"
Write-Host "3. Register normal user"
Write-Host "═══════════════════════════════════════════════════════════"

$userEmail = "testuser$(Get-Random)@test.com"
$reg = @{
    email = $userEmail
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "Regular User"
} | ConvertTo-Json

try {
    $res = Invoke-WebRequest "$baseUrl/auth/register" -Method Post -ContentType "application/json" `
        -Headers @{"X-Tenant-ID"=$tenantId} -Body $reg -UseBasicParsing
    $registerData = $res.Content | ConvertFrom-Json
    $userToken = $registerData.accessToken
    $decoded = Decode-Jwt -token $userToken
    $userId = $decoded.userId
    Write-Host "✅ User registered"
    Write-Host "   UserId: $userId"
    Write-Host "   Email: $userEmail"
    Write-Host "   Role: $($decoded.role)"
} catch {
    Write-Host "❌ Register failed"
    exit 1
}

# Step 4: ADMIN updates the regular user (PUT)
Write-Host ""
Write-Host "═══════════════════════════════════════════════════════════"
Write-Host "4. ADMIN updates the regular user (PUT /users/{id})"
Write-Host "═══════════════════════════════════════════════════════════"

$newEmail = "updated$(Get-Random)@test.com"
$newName = "Updated by Admin"
$update = @{
    name = $newName
    email = $newEmail
    roleId = "00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

Write-Host "Request:"
Write-Host "  PUT /users/$userId"
Write-Host "  Body: $($update | ConvertFrom-Json | ConvertTo-Json -Depth 2)"
Write-Host ""

try {
    $res = Invoke-WebRequest "$baseUrl/users/$userId" -Method Put -ContentType "application/json" `
        -Headers @{"Authorization"="Bearer $adminToken"; "X-Tenant-ID"=$tenantId} `
        -Body $update -UseBasicParsing
    Write-Host "✅✅✅ PUT SUCCESS! ($($res.StatusCode))"
    Write-Host ""
    Write-Host "Response:"
    $updated = $res.Content | ConvertFrom-Json
    Write-Host ($res.Content | ConvertFrom-Json | ConvertTo-Json -Depth 3)
    Write-Host ""
    Write-Host "User updated:"
    Write-Host "  Name: $($updated.name)"
    Write-Host "  Email: $($updated.email)"
    Write-Host "  RoleId: $($updated.roleId)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ PUT ERROR: $statusCode"
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    $error = $reader.ReadToEnd()
    Write-Host "Response: $error"
}
