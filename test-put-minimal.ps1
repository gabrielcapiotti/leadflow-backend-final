$tenantId = "public"
$baseUrl = "http://localhost:8081"

Write-Host "=== MINIMAL TEST === "
Write-Host ""

# Register
$reg = @{
    email = "test-$(Get-Random)@test.com"
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "Test User"
} | ConvertTo-Json

$res = Invoke-WebRequest "$baseUrl/auth/register" -Method POST -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} -Body $reg -UseBasicParsing
$data = $res.Content | ConvertFrom-Json
$token = $data.accessToken
$idMatch = $token | Select-String -Pattern '"userId":"([^"]+)"'
$userId = $idMatch.Matches.Groups[1].Value

Write-Host "[✅] Registered user: $userId"
Write-Host "[✅] Token (first 50 chars): $($token.Substring(0, 50))..."
Write-Host ""

# Test 1: GET own profile
Write-Host "[1] GET /users/$userId (should be 200)..."
try {
    $res = Invoke-WebRequest "$baseUrl/users/$userId" -Headers @{Authorization="Bearer $token"; "X-Tenant-ID"=$tenantId} -UseBasicParsing
    Write-Host "✅ 200 OK"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ $status"
}
Write-Host ""

# Test 2: PUT own profile  
Write-Host "[2] PUT /users/$userId (should be 200)..."
$update = @{
    name = "Updated Name"
    email = "updated-$(Get-Random)@test.com"
    roleId = "00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

try {
    $res = Invoke-WebRequest "$baseUrl/users/$userId" -Method PUT -Headers @{Authorization="Bearer $token"; "X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} -Body $update -UseBasicParsing
    Write-Host "✅ 200 OK"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    $body = $_.Exception.Response.Content.ReadAsString()
    Write-Host "❌ $status"
    Write-Host "   Body: $body"
}
Write-Host ""

# Test 3: Try PUT a different user
Write-Host "[3] Creating a second user..."
$reg2 = @{
    email = "other-$(Get-Random)@test.com"
    password = "Test@123456"
    confirmPassword = "Test@123456"
    name = "Other User"
} | ConvertTo-Json

$res = Invoke-WebRequest "$baseUrl/auth/register" -Method POST -Headers @{"X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} -Body $reg2 -UseBasicParsing
$data2 = $res.Content | ConvertFrom-Json
$idMatch2 = $data2.accessToken | Select-String -Pattern '"userId":"([^"]+)"'
$userId2 = $idMatch2.Matches.Groups[1].Value
Write-Host "✅ User 2: $userId2"
Write-Host ""

# Test  4: PUT user2 with user1's token (should be 403)
Write-Host "[4] PUT /users/$userId2 with user1's token (should be 403)..."
$update2 = @{
    name = "Hacked!"
    email = "hacked-$(Get-Random)@test.com"
    roleId = "00000000-0000-0000-0000-000000000001"
} | ConvertTo-Json

try {
    $res = Invoke-WebRequest "$baseUrl/users/$userId2" -Method PUT -Headers @{Authorization="Bearer $token"; "X-Tenant-ID"=$tenantId; "Content-Type"="application/json"} -Body $update2 -UseBasicParsing
    Write-Host "❌ SECURITY BREACH: Got 200"
} catch {
    $status = $_.Exception.Response.StatusCode.Value__
    $body = $_.Exception.Response.Content.ReadAsString()
    
    if ($status -eq 403) {
        Write-Host "✅ 403 Forbidden (correct)"
    } else {
        Write-Host "❌ Got $status (expected 403)"
        Write-Host "   Body: $body"
    }
}
