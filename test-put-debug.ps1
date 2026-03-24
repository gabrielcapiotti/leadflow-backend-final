function Decode-Jwt {
    param([string]$token)
    $parts = $token.Split('.')
    if ($parts.Count -ne 3) { return $null }
    $payload = $parts[1]
    # Add padding if needed
    $padding = 4 - $payload.Length % 4
    if ($padding -ne 4) { $payload += '=' * $padding }
    return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload)) | ConvertFrom-Json
}

$tenantId = "public"
$baseUrl = "http://localhost:8081"
$email = "puttest$(Get-Random)@test.com"

# Register
$reg = @{
    email = $email
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "Test"
} | ConvertTo-Json

Write-Host "1. Registering with email=$email"
try {
    $res = Invoke-WebRequest "$baseUrl/auth/register" -Method Post -ContentType "application/json" `
        -Headers @{"X-Tenant-ID"=$tenantId} -Body $reg -UseBasicParsing
    $registerData = $res.Content | ConvertFrom-Json
    $token = $registerData.accessToken
    $decoded = Decode-Jwt -token $token
    $userId = $decoded.userId
    Write-Host "✅ Registered with userId=$userId"
    Write-Host "   Token payload: $($decoded | ConvertTo-Json)"
} catch {
    Write-Host "❌ Register failed: Error $($_.Exception.Response.StatusCode.Value__)"
    exit 1
}

# Login
$login = @{email=$email; password="Test@123456"} | ConvertTo-Json

Write-Host "2. Logging in with email=$email"
try {
    $res = Invoke-WebRequest "$baseUrl/auth/login" -Method Post -ContentType "application/json" `
        -Headers @{"X-Tenant-ID"=$tenantId} -Body $login -UseBasicParsing
    $loginData = $res.Content | ConvertFrom-Json
    $token = $loginData.accessToken
    Write-Host "✅ Logged in successfully"
} catch {
    Write-Host "❌ Login failed"
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    $body = $reader.ReadToEnd()
    Write-Host "Response: $body"
    exit 1
}

# PUT - This should work!
$newEmail = "updated$(Get-Random)@test.com"
$update = @{
    name = "Updated"
    email = $newEmail
    roleId = "00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

Write-Host "3. Testing PUT to user=$userId with new email=$newEmail"
try {
    $res = Invoke-WebRequest "$baseUrl/users/$userId" -Method Put -ContentType "application/json" `
        -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"=$tenantId} `
        -Body $update -UseBasicParsing
    Write-Host "✅ PUT SUCCESS ($($res.StatusCode)): $($res.Content | ConvertFrom-Json | ConvertTo-Json -Depth 2)"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ PUT ERROR: $statusCode"
    $stream = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($stream)
    $error = $reader.ReadToEnd()
    Write-Host "Response: $error"
}
