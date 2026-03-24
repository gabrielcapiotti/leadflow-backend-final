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

# Register just one user to isolate the 401 issue
Write-Host "🔍 Registering User for 401 Investigation..."
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

Write-Host "✅ User A: $userA"

# Register B
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
$userB = $dataB.userId

Write-Host "✅ User B: $userB"
Write-Host ""

# NOW: Direct PUT attempt with fresh token
Write-Host "🔍 Testing PUT with decoded token info:"
$expTime = [DateTime]::UnixEpoch.AddSeconds($decodedA.exp)
$now = Get-Date
$timeLeft = ($expTime - $now).TotalSeconds
Write-Host "   Token expires in: $([Math]::Round($timeLeft, 1))s"
Write-Host "   Authenticated as: $($decodedA.sub) (id: $($decodedA.userId))"
Write-Host ""

Write-Host "🔴 Attempting PUT on USER B with USER A token..."
$updateB = @{
    name = "User B Hacked!"
    email = "hacked-$(Get-Random)@test.com"
    roleId = "00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

try {
    $res = Invoke-WebRequest "$baseUrl/users/$userB" `
        -Method PUT `
        -Headers @{
            Authorization="Bearer $tokenA"
            "X-Tenant-ID"=$tenantId
            "Content-Type"="application/json"
        } `
        -Body $updateB `
        -UseBasicParsing
    Write-Host "❌ SECURITY BREACH: Got 200 OK"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    $body = $_.Exception.Response.Content.ReadAsString()
    
    Write-Host "Response Status: $status"
    Write-Host "Response Body: $body"
    Write-Host ""
    
    if ($status -eq 403) {
        Write-Host "✅ CORRECT: 403 Forbidden (ownership denied)"
    } elseif ($status -eq 401) {
        Write-Host "❌ GOT 401 (token invalid) - investigating..."
        Write-Host "   This suggests token expired mid-test"
    } else {
        Write-Host "⚠️  Unexpected status: $status"
    }
}

Write-Host ""
Write-Host "🔍 Retrying immediately..."
try {
    $res = Invoke-WebRequest "$baseUrl/users/$userB" `
        -Method PUT `
        -Headers @{
            Authorization="Bearer $tokenA"
            "X-Tenant-ID"=$tenantId
            "Content-Type"="application/json"
        } `
        -Body $updateB `
        -UseBasicParsing
    Write-Host "❌ SECURITY BREACH: Got 200 OK on retry"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    if ($status -eq 403) {
        Write-Host "✅ Retry SUCCESS: 403 Forbidden"
    } else {
        Write-Host "⚠️  Retry got: $status"
    }
}
