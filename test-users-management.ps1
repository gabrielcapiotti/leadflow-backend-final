# ========================================
# USER MANAGEMENT API TEST SUITE v2.0
# ========================================

$ErrorActionPreference = "Continue"
$server = "http://localhost:8081"

[int]$global:TestCount = 0
[int]$global:Passed = 0
[int]$global:Failed = 0

$global:authToken = $null
$global:testUsers = @()

function Pass($msg) {
    Write-Host "✅ PASS - $msg" -ForegroundColor Green
    $global:Passed++
}

function Fail($msg) {
    Write-Host "❌ FAIL - $msg" -ForegroundColor Red
    $global:Failed++
}

function Test-Response($response, $expected) {
    if ($response.StatusCode -eq $expected) {
        return $true
    }
    return $false
}

# ========================================
# SETUP
# ========================================

Write-Host "`n[SETUP] Health Check" -ForegroundColor Yellow
try {
    $health = Invoke-WebRequest "$server/actuator/health" -Headers @{"X-Tenant-ID"="public"} -UseBasicParsing
    if ($health.StatusCode -eq 200) { Pass "Server online" }
} catch { Fail "Server offline"; exit }

# ========================================
# AUTH
# ========================================

Write-Host "`n[SETUP] Authentication" -ForegroundColor Yellow

$adminEmail = "admin-$(Get-Random 999999999)@leadflow.com"
$adminPassword = "Admin@123456"

$registerPayload = @{
    email = $adminEmail
    password = $adminPassword
    confirmPassword = $adminPassword
    name = "Admin"
} | ConvertTo-Json

try {
    Invoke-WebRequest "$server/auth/register" -Method POST -Body $registerPayload -Headers @{"Content-Type"="application/json"; "X-Tenant-ID"="public"} -UseBasicParsing -ErrorAction Stop | Out-Null
} catch {
    Write-Host "Register error: $($_.Exception.Response.StatusCode)" -ForegroundColor Yellow
}

$loginPayload = @{
    email = $adminEmail
    password = $adminPassword
} | ConvertTo-Json

try {
    $login = Invoke-WebRequest "$server/auth/login" -Method POST -Body $loginPayload -Headers @{"Content-Type"="application/json"; "X-Tenant-ID"="public"} -UseBasicParsing -ErrorAction Stop
    $global:authToken = ($login.Content | ConvertFrom-Json).accessToken
    Pass "Authenticated"
} catch {
    Fail "Login failed: $($_.Exception.Response.StatusCode)"
    exit
}

# ========================================
# CREATE USERS
# ========================================

Write-Host "`n[SETUP] Creating users" -ForegroundColor Yellow

for ($i=1; $i -le 2; $i++) {
    $email = "user$i-$(Get-Random 999999999)@leadflow.com"

    $payload = @{
        email=$email
        password="Test@123456"
        confirmPassword="Test@123456"
        name="User $i"
    } | ConvertTo-Json

    $res = Invoke-WebRequest "$server/auth/register" -Method POST -Body $payload -Headers @{"Content-Type"="application/json"; "X-Tenant-ID"="public"} -UseBasicParsing

    if ($res.StatusCode -eq 201) {
        $data = $res.Content | ConvertFrom-Json
        $global:testUsers += $data
        Pass "User $i created"
    }
}

# ========================================
# TESTS
# ========================================

Write-Host "`n[TESTS]" -ForegroundColor Cyan

# GET /users
$global:TestCount++
try {
    $res = Invoke-WebRequest "$server/users?page=0&size=10" -Headers @{Authorization="Bearer $authToken"; "X-Tenant-ID"="public"} -UseBasicParsing

    if ($res.StatusCode -eq 200) {
        $data = $res.Content | ConvertFrom-Json
        if ($data.content) {
            Pass "List users working"
        } else {
            Fail "Empty response"
        }
    }
}
catch {
    Fail "GET /users failed ($($_.Exception.Response.StatusCode.value__))"
}

# GET user
$global:TestCount++
$id = $global:testUsers[0].id
try {
    $res = Invoke-WebRequest "$server/users/$id" -Headers @{Authorization="Bearer $authToken"; "X-Tenant-ID"="public"} -UseBasicParsing -ErrorAction Stop
    if ($res.StatusCode -eq 200) { Pass "Get user OK" }
}
catch { Fail "GET user failed ($($_.Exception.Response.StatusCode.value__))" }

# UPDATE
$global:TestCount++
try {
    $payload = @{
        name="Updated User"
        email=$global:testUsers[0].email
        roleId="00000000-0000-0000-0000-000000000001"
    } | ConvertTo-Json

    $res = Invoke-WebRequest "$server/users/$id" -Method PUT -Body $payload -Headers @{
        Authorization="Bearer $authToken"
        "Content-Type"="application/json"
        "X-Tenant-ID"="public"
    } -UseBasicParsing -ErrorAction Stop

    if ($res.StatusCode -eq 200) { Pass "Update OK" }
}
catch { Fail "Update failed ($($_.Exception.Response.StatusCode.value__))" }

# DELETE
$global:TestCount++
$id2 = $global:testUsers[1].id
try {
    $res = Invoke-WebRequest "$server/users/$id2" -Method DELETE -Headers @{
        Authorization="Bearer $authToken"
        "X-Tenant-ID"="public"
    } -UseBasicParsing -ErrorAction Stop

    if ($res.StatusCode -eq 204) { Pass "Delete OK" }
}
catch { Fail "Delete failed ($($_.Exception.Response.StatusCode.value__))" }

# ========================================
# SUMMARY
# ========================================

Write-Host "`n========================================"
Write-Host "TOTAL: $global:TestCount"
Write-Host "PASSED: $global:Passed"
Write-Host "FAILED: $global:Failed"

$rate = [math]::Round(($global:Passed/$global:TestCount)*100,1)
Write-Host "PASS RATE: $rate%"
Write-Host "========================================"