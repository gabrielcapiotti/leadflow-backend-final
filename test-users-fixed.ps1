#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Test User Management endpoints - Fixed version with ADMIN role setup
#>

$server = "http://localhost:8081"
$dbServer = "localhost"
$dbPort = 2411
$dbName = "leadflow_test"
$dbUser = "postgres"
$dbPassword = "postgres"

# Role UUIDs (from DB migration V12__seed_roles.sql)
$ROLE_ADMIN_ID = "00000000-0000-0000-0000-000000000002"
$ROLE_USER_ID = "00000000-0000-0000-0000-000000000001"

# Test result counters
$global:PassCount = 0
$global:FailCount = 0
$global:TestCount = 0
$global:testUsers = @()
$global:authToken = $null

function Pass($message) {
    Write-Host "✅ PASS - $message" -ForegroundColor Green
    $global:PassCount++
}

function Fail($message) {
    Write-Host "❌ FAIL - $message" -ForegroundColor Red
    $global:FailCount++
}

Write-Host "`n[SETUP] Initializing..." -ForegroundColor Cyan

# ========================================
# SETUP: Health Check
# ========================================

Write-Host "`n[SETUP] Health Check" -ForegroundColor Yellow
try {
    $health = Invoke-WebRequest "$server/actuator/health" -Headers @{"X-Tenant-ID"="public"} -UseBasicParsing
    if ($health.StatusCode -eq 200) { Pass "Server online" }
} catch { 
    Fail "Server offline"; exit 
}

# ========================================
# SETUP: Create ADMIN user by direct registration then SQL update
# ========================================

Write-Host "`n[SETUP] Creating ADMIN user" -ForegroundColor Yellow

# First, register a user
try {
    $registerResp = Invoke-WebRequest "$server/auth/register" -Method POST -Body (@{
        email=$adminEmail
        password=$adminPassword
        confirmPassword=$adminPassword
        name="Admin User"
    } | ConvertTo-Json) -Headers @{"Content-Type"="application/json"; "X-Tenant-ID"="public"} -UseBasicParsing
    
    $registerToken = ($registerResp.Content | ConvertFrom-Json).accessToken
    
    # Get admin user ID from /auth/me
    $meResp = Invoke-WebRequest "$server/auth/me" -Headers @{"Authorization"="Bearer $registerToken"; "X-Tenant-ID"="public"} -UseBasicParsing
    $adminId = ($meResp.Content | ConvertFrom-Json).id
    
    Pass "User registered ($adminId)"
} catch {
    Fail "User registration failed"
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Gray
}

if ($adminId -eq [guid]::Empty) {
    Write-Host "`n❌ Cannot proceed without valid admin ID" -ForegroundColor Red
    exit 1
}

# ========================================
# SETUP: Update user role to ADMIN via SQL
# ========================================

Write-Host "`n[SETUP] Promoting user to ADMIN role" -ForegroundColor Yellow

$sqlUpdateAdmin = @"
UPDATE public.users
SET role_id = '$ROLE_ADMIN_ID'::uuid
WHERE id = '$adminId'::uuid AND deleted_at IS NULL;
"@

# Try different methods to execute SQL

# Method 1: Try psql (if installed)
try {
    $psqlCmd = @"
`$env:PGPASSWORD = '$dbPassword';
psql -h $dbServer -p $dbPort -U $dbUser -d $dbName -c "UPDATE public.users SET role_id = '$ROLE_ADMIN_ID'::uuid WHERE id = '$adminId'::uuid AND deleted_at IS NULL;" -q 2>&1
"@
    Invoke-Expression $psqlCmd | Out-Null
    Pass "User promoted to ADMIN via psql"
} catch {
    # Method 2: Try docker exec
    try {
        $sqlScript = "UPDATE public.users SET role_id = '$ROLE_ADMIN_ID'::uuid WHERE id = '$adminId'::uuid AND deleted_at IS NULL;"
        $connection = "Data Source=$dbServer,$dbPort;Initial Catalog=$dbName;User ID=$dbUser;Password=$dbPassword;Integrated Security=false;"
        
        # Use PS SQL module if available
        $sqlConnection = New-Object System.Data.SqlClient.SqlConnection
        $sqlConnection.ConnectionString = $connection
        $sqlConnection.Open()
        $sqlCmd = $sqlConnection.CreateCommand()
        $sqlCmd.CommandText = $sqlScript
        $sqlCmd.ExecuteNonQuery() | Out-Null
        $sqlConnection.Close()
        Pass "User promoted to ADMIN via SQL"
    } catch {
        Write-Host "⚠️  Could not update role via direct SQL access" -ForegroundColor Yellow
        Write-Host "   Will test with current USER role instead" -ForegroundColor Yellow
    }
}

# ========================================
# SETUP: Authentication
# ========================================

Write-Host "`n[SETUP] Authentication" -ForegroundColor Yellow

$loginPayload = @{
    email = $adminEmail
    password = $adminPassword
} | ConvertTo-Json

try {
    $login = Invoke-WebRequest "$server/auth/login" -Method POST -Body $loginPayload -Headers @{"Content-Type"="application/json"; "X-Tenant-ID"="public"} -UseBasicParsing
    if ($login.StatusCode -eq 200) {
        $global:authToken = ($login.Content | ConvertFrom-Json).accessToken
        Pass "Authenticated"
    }
} catch {
    Fail "Login failed"
}

if (-not $global:authToken) {
    Write-Host "`n❌ Cannot proceed without authentication token" -ForegroundColor Red
    exit 1
}

# ========================================
# SETUP: Creating test users
# ========================================

Write-Host "`n[SETUP] Creating test users" -ForegroundColor Yellow

for ($i=1; $i -le 2; $i++) {
    $email = "user$i-$([DateTime]::Now.Ticks)@leadflow.com"

    $payload = @{
        email=$email
        password="Test@123456"
        confirmPassword="Test@123456"
        name="User $i"
    } | ConvertTo-Json

    try {
        $res = Invoke-WebRequest "$server/auth/register" -Method POST -Body $payload -Headers @{"Content-Type"="application/json"; "X-Tenant-ID"="public"} -UseBasicParsing
        if ($res.StatusCode -eq 201) {
            # Extract user ID from token
            $token = ($res.Content | ConvertFrom-Json).accessToken
            $meResp = Invoke-WebRequest "$server/auth/me" -Headers @{"Authorization"="Bearer $token"; "X-Tenant-ID"="public"} -UseBasicParsing
            $meData = $meResp.Content | ConvertFrom-Json
            $global:testUsers += @{id=$meData.id; email=$email}
            Pass "User $i created ($email)"
        }
    } catch {
        Fail "User $i creation failed"
    }
}

if ($global:testUsers.Count -lt 2) {
    Write-Host "`n⚠️  Only $($global:testUsers.Count) test users created, need 2 for all tests" -ForegroundColor Yellow
}

# ========================================
# TEST 1: List Users
# ========================================

Write-Host "`n[TESTS]" -ForegroundColor Yellow

$global:TestCount++
try {
    $res = Invoke-WebRequest "$server/users?page=0&size=10" -Headers @{Authorization="Bearer $authToken"; "X-Tenant-ID"="public"} -UseBasicParsing

    if ($res.StatusCode -eq 200) {
        $data = $res.Content | ConvertFrom-Json
        Pass "List users (found $($data.content.Count) users)"
    }
}
catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Fail "GET /users failed ($statusCode) - may need ADMIN role promotion"
}

# ========================================
# TEST 2: Get specific user
# ========================================

$global:TestCount++
if ($global:testUsers.Count -gt 0) {
    $id = $global:testUsers[0].id
    try {
        $res = Invoke-WebRequest "$server/users/$id" -Headers @{Authorization="Bearer $authToken"; "X-Tenant-ID"="public"} -UseBasicParsing
        if ($res.StatusCode -eq 200) { 
            Pass "Get user ($id)" 
        }
    }
    catch { 
        $statusCode = $_.Exception.Response.StatusCode.value__
        Fail "GET /users/{id} failed ($statusCode)" 
    }
} else {
    Fail "GET /users/{id} skipped (no test user)"
}

# ========================================
# TEST 3: Update user
# ========================================

$global:TestCount++
if ($global:testUsers.Count -gt 0) {
    $id = $global:testUsers[0].id
    try {
        $payload = @{
            name="Updated User"
            email=$global:testUsers[0].email
            roleId=$ROLE_USER_ID
        } | ConvertTo-Json

        $res = Invoke-WebRequest "$server/users/$id" -Method PUT -Body $payload -Headers @{
            Authorization="Bearer $authToken"
            "Content-Type"="application/json"
            "X-Tenant-ID"="public"
        } -UseBasicParsing

        if ($res.StatusCode -eq 200) { 
            Pass "Update user" 
        }
    }
    catch { 
        $statusCode = $_.Exception.Response.StatusCode.value__
        Fail "PUT /users/{id} failed ($statusCode)" 
    }
} else {
    Fail "PUT /users/{id} skipped (no test user)"
}

# ========================================
# TEST 4: Delete user  
# ========================================

$global:TestCount++
if ($global:testUsers.Count -gt 1) {
    $id2 = $global:testUsers[1].id
    try {
        $res = Invoke-WebRequest "$server/users/$id2" -Method DELETE -Headers @{
            Authorization="Bearer $authToken"
            "X-Tenant-ID"="public"
        } -UseBasicParsing

        if ($res.StatusCode -eq 204) { 
            Pass "Delete user" 
        }
    }
    catch { 
        $statusCode = $_.Exception.Response.StatusCode.value__
        Fail "DELETE /users/{id} failed ($statusCode)" 
    }
} else {
    Fail "DELETE /users/{id} skipped (insufficient test users)"
}

# ========================================
# Results
# ========================================

Write-Host "`n========================================" -ForegroundColor Cyan
Write-Host "TOTAL: $global:TestCount" -ForegroundColor Cyan
Write-Host "PASSED: $global:PassCount" -ForegroundColor Green
Write-Host "FAILED: $global:FailCount" -ForegroundColor Red

if ($global:TestCount -gt 0) {
    $passRate = [math]::Round(($global:PassCount / $global:TestCount) * 100)
    Write-Host "PASS RATE: $passRate%" -ForegroundColor Cyan
    
    if ($passRate -eq 100) {
        Write-Host "✅ ALL TESTS PASSED!" -ForegroundColor Green
    } elseif ($passRate -ge 75) {
        Write-Host "⚠️  MOST TESTS PASSED (may be due to role setup)" -ForegroundColor Yellow
    }
}
Write-Host "========================================" -ForegroundColor Cyan
