#!/usr/bin/env pwsh
<#
.SYNOPSIS
    Test User Management - Simplified version that handles errors properly
#>

$ErrorActionPreference = "Stop"
$server = "http://localhost:8081"

# Test result counters
$script:PassCount = 0
$script:FailCount = 0

function Pass($msg) {
    Write-Host "✅ $msg" -ForegroundColor Green
    $script:PassCount++
}

function Fail($msg) {
    Write-Host "❌ $msg" -ForegroundColor Red
    $script:FailCount++
}

Write-Host "`n════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "USER MANAGEMENT ENDPOINTS - TEST SUITE" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════`n" -ForegroundColor Cyan

# ========================================
# 1. Health Check
# ========================================
Write-Host "[1] Health Check..." -ForegroundColor Yellow
try {
    $resp = Invoke-WebRequest "$server/actuator/health" -Headers @{"X-Tenant-ID"="public"} -UseBasicParsing -ErrorAction Stop
    if ($resp.StatusCode -eq 200) { Pass "Server online" } else { Fail "Health check returned $($resp.StatusCode)" }
} catch {
    Fail "Server offline: $($_.Exception.Message)"
    exit 1
}

# ========================================
# 2. Register Users
# ========================================
Write-Host "`n[2] Registering test users..." -ForegroundColor Yellow

$testUsers = @()
$testPasswords = @()

for ($i = 1; $i -le 3; $i++) {
    $email = "user$i-$(Get-Random)@leadflow.com"
    $password = "Test@123456"
    
    try {
        $payload = @{
            email = $email
            password = $password
            confirmPassword = $password
            name = "Test User $i"
        } | ConvertTo-Json
        
        $resp = Invoke-WebRequest "$server/auth/register" `
            -Method POST `
            -Body $payload `
            -Headers @{"Content-Type"="application/json"; "X-Tenant-ID"="public"} `
            -UseBasicParsing `
            -ErrorAction Stop
        
        if ($resp.StatusCode -eq 201) {
            $userData = $resp.Content | ConvertFrom-Json
            $testUsers += @{
                email = $email
                password = $password
                id = $null  # Will get from /auth/me
            }
            Pass "User $i registered: $email"
        }
    } catch {
        Fail "User $i registration failed: $($_.Exception.Message)"
    }
}

if ($testUsers.Count -eq 0) {
    Write-Host "`n❌ No users registered, cannot proceed with tests" -ForegroundColor Red
    exit 1
}

# ========================================
# 3. Login and get tokens
# ========================================
Write-Host "`n[3] Authenticating users..." -ForegroundColor Yellow

$tokens = @()
foreach ($user in $testUsers) {
    try {
        $loginPayload = @{
            email = $user.email
            password = $user.password
        } | ConvertTo-Json
        
        $resp = Invoke-WebRequest "$server/auth/login" `
            -Method POST `
            -Body $loginPayload `
            -Headers @{"Content-Type"="application/json"; "X-Tenant-ID"="public"} `
            -UseBasicParsing `
            -ErrorAction Stop
        
        if ($resp.StatusCode -eq 200) {
            $tokenData = $resp.Content | ConvertFrom-Json
            $user.token = $tokenData.accessToken
            Pass "User authenticated: $($user.email)"
            
            # Get user ID from /auth/me
            try {
                $meResp = Invoke-WebRequest "$server/auth/me" `
                    -Headers @{"Authorization"="Bearer $($user.token)"; "X-Tenant-ID"="public"} `
                    -UseBasicParsing `
                    -ErrorAction Stop
                
                $meData = $meResp.Content | ConvertFrom-Json
                $user.id = $meData.id
            } catch {
                Write-Host "  ⚠️  Could not get user ID: $($_.Exception.Message)" -ForegroundColor Gray
            }
        }
    } catch {
        Fail "Login failed for $($user.email): $($_.Exception.Message)"
    }
}

# ========================================
# 4. Test endpoints with first user
# ========================================
if ($testUsers[0].token) {
    Write-Host "`n[4] Testing CRUD endpoints..." -ForegroundColor Yellow
    $adminToken = $testUsers[0].token
    $adminEmail = $testUsers[0].email
    $testUserId = $testUsers[1].id
    $ROLE_USER_ID = "00000000-0000-0000-0000-000000000001"
    
    # Test GET /users
    try {
        $resp = Invoke-WebRequest "$server/users?page=0&size=10" `
            -Headers @{"Authorization"="Bearer $adminToken"; "X-Tenant-ID"="public"} `
            -UseBasicParsing `
            -ErrorAction Stop
        
        if ($resp.StatusCode -eq 200) {
            $data = $resp.Content | ConvertFrom-Json
            Pass "GET /users - Found $($data.content.Count) users"
        }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        if ($statusCode -eq 403) {
            Fail "GET /users - 403 Forbidden (user doesn't have ADMIN role)"
        } else {
            Fail "GET /users - HTTP $statusCode"
        }
    }
    
    # Test GET /users/{id}
    if ($testUserId) {
        try {
            $resp = Invoke-WebRequest "$server/users/$testUserId" `
                -Headers @{"Authorization"="Bearer $adminToken"; "X-Tenant-ID"="public"} `
                -UseBasicParsing `
                -ErrorAction Stop
            
            if ($resp.StatusCode -eq 200) {
                Pass "GET /users/{id} - Retrieved user $testUserId"
            }
        } catch {
            $statusCode = $_.Exception.Response.StatusCode.value__
            if ($statusCode -eq 403) {
                Fail "GET /users/{id} - 403 Forbidden"
            } else {
                Fail "GET /users/{id} - HTTP $statusCode"
            }
        }
    }
    
    # Test PUT /users/{id}
    if ($testUserId) {
        try {
            $updatePayload = @{
                name = "Updated by Test"
                email = $testUsers[1].email
                roleId = $ROLE_USER_ID
            } | ConvertTo-Json
            
            $resp = Invoke-WebRequest "$server/users/$testUserId" `
                -Method PUT `
                -Body $updatePayload `
                -Headers @{"Authorization"="Bearer $adminToken"; "Content-Type"="application/json"; "X-Tenant-ID"="public"} `
                -UseBasicParsing `
                -ErrorAction Stop
            
            if ($resp.StatusCode -eq 200) {
                Pass "PUT /users/{id} - Updated user successfully"
            }
        } catch {
            $statusCode = $_.Exception.Response.StatusCode.value__
            Fail "PUT /users/{id} - HTTP $statusCode"
        }
    }
    
    # Test DELETE /users/{id}
    if ($testUsers.Count -gt 2 -and $testUsers[2].id) {
        try {
            $resp = Invoke-WebRequest "$server/users/$($testUsers[2].id)" `
                -Method DELETE `
                -Headers @{"Authorization"="Bearer $adminToken"; "X-Tenant-ID"="public"} `
                -UseBasicParsing `
                -ErrorAction Stop
            
            if ($resp.StatusCode -eq 204) {
                Pass "DELETE /users/{id} - Deleted user successfully"
            }
        } catch {
            $statusCode = $_.Exception.Response.StatusCode.value__
            if ($statusCode -eq 403) {
                Fail "DELETE /users/{id} - 403 Forbidden"
            } else {
                Fail "DELETE /users/{id} - $statusCode"
            }
        }
    }
}

# ========================================
# Results
# ========================================
Write-Host "`n════════════════════════════════════════════" -ForegroundColor Cyan
$total = $script:PassCount + $script:FailCount
if ($total -gt 0) {
    $passRate = [math]::Round(($script:PassCount / $total) * 100)
    Write-Host "TOTAL: $total" -ForegroundColor Cyan
    Write-Host "PASSED: $script:PassCount" -ForegroundColor Green
    Write-Host "FAILED: $script:FailCount" -ForegroundColor Red
    Write-Host "PASS RATE: $passRate%" -ForegroundColor Cyan
    
    if ($passRate -eq 100) {
        Write-Host "✅ ALL TESTS PASSED!" -ForegroundColor Green
    }
} else {
    Write-Host "⚠️  No tests executed" -ForegroundColor Yellow
}
Write-Host "════════════════════════════════════════════`n" -ForegroundColor Cyan
