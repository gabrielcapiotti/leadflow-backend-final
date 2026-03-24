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

Write-Host "================================"
Write-Host "TEST: User PUT to own profile"
Write-Host "================================"

# Register
$reg = @{
    email = "testuser-$(Get-Random)@test.com"
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "Test User"
} | ConvertTo-Json

$res = Invoke-WebRequest "$baseUrl/auth/register" `
    -Method POST `
    -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} `
    -Body $reg `
    -UseBasicParsing

$data = $res.Content | ConvertFrom-Json
$token = $data.accessToken
$decoded = Decode-Jwt $token
$userId = $decoded.userId

Write-Host "✅ User registered: $userId"
Write-Host "   Email: $($decoded.sub)"

# PUT own profile  
$update = @{
    name = "Updated Name"
    email = "updated$(Get-Random)@test.com"
    roleId = "00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

Write-Host ""
Write-Host "PUT $baseUrl/users/$userId"
Write-Host "Token user: $($decoded.sub)"

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
    Write-Host "✅ PUT succeeded: 200 OK"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ PUT failed: $status"
    try {
        $body = [System.IO.StreamReader]::new($_.Exception.Response.GetResponseStream()).ReadToEnd()
        Write-Host "Error: $body"
    } catch {}
}

Write-Host ""
Write-Host "================================"
Write-Host "TEST: GET own profile after PUT"
Write-Host "================================"

try {
    $res = Invoke-WebRequest "$baseUrl/users/$userId" `
        -Headers @{
            Authorization="Bearer $token"
            "X-Tenant-ID"=$tenantId
        } `
        -UseBasicParsing
    $body = $res.Content | ConvertFrom-Json
    Write-Host "✅ GET succeeded: 200 OK"
    Write-Host "   Name: $($body.name)"
    Write-Host "   Email: $($body.email)"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ GET failed: $status"
}
