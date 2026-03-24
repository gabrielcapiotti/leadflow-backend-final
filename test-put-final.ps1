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
$email = "puttest$(Get-Random)@test.com"

# Register
$reg = @{
    email = $email
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "Test User"
} | ConvertTo-Json

Write-Host "1. Registering with email=$email"
try {
    $res = Invoke-WebRequest "$baseUrl/auth/register" -Method Post -ContentType "application/json" `
        -Headers @{"X-Tenant-ID"=$tenantId} -Body $reg -UseBasicParsing
    $registerData = $res.Content | ConvertFrom-Json
    $token = $registerData.accessToken
    $decoded = Decode-Jwt -token $token
    $userId = $decoded.userId
    Write-Host "✅ Registered with userId=$userId (role=ROLE_USER)"
} catch {
    Write-Host "❌ Register failed"
    exit 1
}

# Promote to ADMIN in database
Write-Host "2. Promoting user to ADMIN in database..."
$promoteQuery = @"
UPDATE users 
SET role_id = (SELECT id FROM roles WHERE name = 'ROLE_ADMIN' LIMIT 1)
WHERE id = '$userId'::uuid AND tenant_id = '$tenantId'
"@

$env:PGPASSWORD = $psqlPassword
& psql -h $psqlHost -p $psqlPort -U $psqlUser -d leadflow -c $promoteQuery 2>$null | Out-Null
if ($LASTEXITCODE -eq 0) {
    Write-Host "✅ User promoted to ADMIN"
} else {
    Write-Host "⚠️  Promotion may have failed, continuing anyway..."
}

# Login to get new token with ADMIN role
$login = @{email=$email; password="Test@123456"} | ConvertTo-Json

Write-Host "3. Logging in with ADMIN token..."
try {
    $res = Invoke-WebRequest "$baseUrl/auth/login" -Method Post -ContentType "application/json" `
        -Headers @{"X-Tenant-ID"=$tenantId} -Body $login -UseBasicParsing
    $loginData = $res.Content | ConvertFrom-Json
    $token = $loginData.accessToken
    $decoded = Decode-Jwt -token $token
    Write-Host "✅ Logged in, role=$($decoded.role)"
} catch {
    Write-Host "❌ Login failed"
    exit 1
}

# PUT - This should work now!
$newEmail = "updated$(Get-Random)@test.com"
$newName = "Updated Name $(Get-Random)"
$update = @{
    name = $newName
    email = $newEmail
    roleId = "00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

Write-Host "4. Testing PUT to update user=$userId"
Write-Host "   New name: $newName"
Write-Host "   New email: $newEmail"
try {
    $res = Invoke-WebRequest "$baseUrl/users/$userId" -Method Put -ContentType "application/json" `
        -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"=$tenantId} `
        -Body $update -UseBasicParsing
    Write-Host "✅✅✅ PUT SUCCESS! ($($res.StatusCode))"
    $updated = $res.Content | ConvertFrom-Json
    Write-Host "Response: $($res.Content | ConvertFrom-Json | ConvertTo-Json -Depth 2)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ PUT ERROR: $statusCode"
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    $error = $reader.ReadToEnd()
    Write-Host "Response: $error"
}
