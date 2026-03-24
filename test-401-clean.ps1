function Decode-Jwt {
    param([string]$token)
    $parts = $token.Split('.')
    if ($parts.Count -ne 3) { return $null }
    $payload = $parts[1]
    $padding = 4 - $payload.Length % 4
    if ($padding -ne 4) { $payload += '=' * $padding }
    try {
        return [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload)) | ConvertFrom-Json
    } catch { return $null }
}

$baseUrl = "http://localhost:8081"
$tenantId = "public"

Write-Host "🔍 ===== 401 DIAGNOSIS ====="
Write-Host ""

# Register user1
$reg1 = @{email="test1-$(Get-Random)@test.com"; password="Test@123456"; confirmPassword="Test@123456"; name="Test1"} | ConvertTo-Json
$res1 = Invoke-WebRequest "$baseUrl/auth/register" -Method POST -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} -Body $reg1 -UseBasicParsing
$token1 = ($res1.Content | ConvertFrom-Json).accessToken
$user1 = (Decode-Jwt $token1).userId

# Register user2
$reg2 = @{email="test2-$(Get-Random)@test.com"; password="Test@123456"; confirmPassword="Test@123456"; name="Test2"} | ConvertTo-Json
$res2 = Invoke-WebRequest "$baseUrl/auth/register" -Method POST -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} -Body $reg2 -UseBasicParsing
$user2 = ($res2.Content | ConvertFrom-Json).accessToken | ForEach-Object { (Decode-Jwt $_).userId }

Write-Host "✅ User1: $user1"
Write-Host "✅ User2: $user2"
Write-Host ""

# Test PUT own (should 200)
$update = @{name="Updated"; email="upd-$(Get-Random)@test.com"; roleId="00000000-0000-0000-0000-000000000001"} | ConvertTo-Json
Write-Host "📤 PUT /users/$user1 (own profile)..."
try {
    $res = Invoke-WebRequest "$baseUrl/users/$user1" -Method PUT -Headers @{Authorization="Bearer $token1"; "X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} -Body $update -UseBasicParsing
    Write-Host "✅ 200 OK"
} catch {
    Write-Host "❌ $($_.Exception.Response.StatusCode.Value__)"
}

Write-Host ""

# Test PUT other (should 403, but returns 401)
$update2 = @{name="Hacked!"; email="hack-$(Get-Random)@test.com"; roleId="00000000-0000-0000-0000-000000000001"} | ConvertTo-Json
Write-Host "📤 PUT /users/$user2 (OTHER profile)..."

try {
    $res = Invoke-WebRequest "$baseUrl/users/$user2" -Method PUT -Headers @{Authorization="Bearer $token1"; "X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} -Body $update2 -UseBasicParsing
    Write-Host "❌ SECURITY: 200 OK"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    
    if ($status -eq 401) {
        Write-Host "❌ Got 401 (PROBLEM)"
    } elseif ($status -eq 403) {
        Write-Host "✅ Got 403 (correct)"
    } else {
        Write-Host "⚠️  Got $status"
    }
}
