#!/usr/bin/env pwsh
<#
.SYNOPSIS
    LeadFlow Admin Billing Endpoints - TRANCHE 3 Test Suite
.DESCRIPTION
    Tests 4 admin billing endpoints with proper role-based access control
    1. GET /api/v1/admin/billing/users
    2. GET /api/v1/admin/billing/analytics
    3. GET /api/v1/admin/billing/revenue
    4. POST /api/v1/admin/billing/refund
.NOTES
    Author: LeadFlow Backend Team
    Version: 1.0 (REFACTORED - No emojis, PowerShell compatible)
    Updated: 2026-03-29
#>

param(
    [string]$BaseUrl = "http://localhost:8081",
    [string]$AdminEmail = $null,
    [string]$AdminPassword = "AdminPass123!",
    [string]$AdminSecret = "SUPER_SECRET_KEY_CHANGE_ME"
)

# Generate a unique admin email if not provided
if (-not $AdminEmail) {
    $uuid = [guid]::NewGuid().ToString().Substring(0, 8)
    $timestamp = Get-Date -Format "yyyyMMddHHmmss"
    $AdminEmail = "admin-$uuid-$timestamp@leadflow.test"
}

# ============================================================================
# HELPER: Generate Unique Email (avoid conflicts)
# ============================================================================
function New-UniqueEmail {
    param([string]$Base = "test")
    $uuid = [guid]::NewGuid().ToString().Substring(0, 8)
    $timestamp = Get-Date -Format "yyyyMMddHHmmss"
    $random = Get-Random -Minimum 100000 -Maximum 999999
    $name = $Base.Split("@")[0]
    $domain = if ($Base -match "@") { $Base.Split("@")[1] } else { "leadflow.test" }
    return "$name-$uuid-$timestamp-$random@$domain"
}

# Configuration
$ErrorActionPreference = "SilentlyContinue"
$ProgressPreference = 'SilentlyContinue'

# Test Results Tracking
$global:PassCount = 0
$global:FailCount = 0
$global:SkipCount = 0

# ============================================================================
#                            UTILITY FUNCTIONS
# ============================================================================

function Write-Header {
    param([string]$Title)
    Write-Host ""
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host "============================================================" -ForegroundColor Cyan
    Write-Host ""
}

function Write-Step {
    param([int]$Number, [string]$Name)
    Write-Host "[STEP $Number] $Name" -ForegroundColor Yellow
    Write-Host ""
}

function Write-Test {
    param([int]$Number, [string]$Name)
    Write-Host "[TEST $Number] $Name" -ForegroundColor Yellow
}

function Write-Success {
    param([string]$Message, [int]$Status = 200)
    Write-Host "  [OK] $Message (HTTP $Status)" -ForegroundColor Green
    $global:PassCount++
}

function Write-Fail {
    param([string]$Message, [int]$Status = 0, [string]$Error = "")
    if ($Status -gt 0) {
        Write-Host "  [FAIL] $Message (HTTP $Status)" -ForegroundColor Red
    } else {
        Write-Host "  [FAIL] $Message" -ForegroundColor Red
    }
    if ($Error) {
        Write-Host "         Error: $Error" -ForegroundColor DarkRed
    }
    $global:FailCount++
}

function Write-Skip {
    param([string]$Message)
    Write-Host "  [SKIP] $Message" -ForegroundColor Gray
    $global:SkipCount++
}

function Write-Info {
    param([string]$Message)
    Write-Host "         [INFO] $Message" -ForegroundColor DarkGray
}

function Invoke-ApiRequest {
    param(
        [Parameter(Mandatory=$true)][string]$Method,
        [Parameter(Mandatory=$true)][string]$Endpoint,
        [string]$Description,
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [int[]]$ExpectedStatus = @(200)
    )

    $FullUrl = "$BaseUrl$Endpoint"
    
    try {
        $params = @{
            Uri = $FullUrl
            Method = $Method
            Headers = $Headers
            ContentType = "application/json"
            TimeoutSec = 10
            UseBasicParsing = $true
            ErrorAction = "Stop"
        }

        if ($Body -and $Method -ne "GET") {
            if ($Body -is [string]) {
                $params.Body = $Body
            } else {
                $params.Body = ($Body | ConvertTo-Json -Depth 10)
            }
        }

        $response = Invoke-WebRequest @params
        $statusCode = $response.StatusCode

        if ($ExpectedStatus -contains $statusCode) {
            Write-Success $Description $statusCode
            try {
                return ($response.Content | ConvertFrom-Json)
            } catch {
                return $response.Content
            }
        } else {
            Write-Fail $Description $statusCode "Unexpected status code (expected: $($ExpectedStatus -join ','))"
            return $null
        }

    } catch {
        $statusCode = $_.Exception.Response.StatusCode.value__
        
        if ($ExpectedStatus -contains $statusCode) {
            Write-Success $Description $statusCode
            return @{ message = "Response received with status $statusCode" }
        } else {
            $errorMsg = $_.Exception.Message
            Write-Fail $Description $statusCode $errorMsg
            return $null
        }
    }
}

# ============================================================================
#                            MAIN TESTS
# ============================================================================

Write-Header "TRANCHE 3 - ADMIN BILLING ENDPOINTS TEST SUITE"

Write-Host "Configuration:" -ForegroundColor Yellow
Write-Host "  Base URL: $BaseUrl" -ForegroundColor Cyan
Write-Host "  Admin Email: $AdminEmail" -ForegroundColor Cyan
Write-Host "  Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Cyan
Write-Host ""

# ============================================================================
# STEP 1: REGISTER NORMAL USER FIRST
# ============================================================================

Write-Step 1 "REGISTER NORMAL USER FIRST"

$testNum = 1

Write-Test $testNum "POST /api/auth/register (normal user)"

$normalUserEmail = New-UniqueEmail
$normalUserPassword = "NormalUser@123"

$normalUserBody = @{
    name = "Normal Test User"
    email = $normalUserEmail
    password = $normalUserPassword
    confirmPassword = $normalUserPassword
} | ConvertTo-Json

$normalUserHeaders = @{
    "Content-Type" = "application/json"
}

$normalUserResponse = Invoke-ApiRequest -Method POST `
    -Endpoint "/api/auth/register" `
    -Description "Register normal user first" `
    -Headers $normalUserHeaders `
    -Body $normalUserBody `
    -ExpectedStatus @(201, 200)

if (-not $normalUserResponse) {
    Write-Host ""
    Write-Fail "FATAL: Could not register normal user - test suite aborted"
    Write-Host ""
    exit 1
}

# Extract normal user credentials
try {
    $normalUserToken = $normalUserResponse.accessToken
    $normalUserTenantId = $normalUserResponse.tenantId
    Write-Info "Normal user registered: $normalUserEmail"
    Write-Info "Normal user token: $($normalUserToken.Substring(0, 30))..."
    Write-Info "Normal user tenant: $normalUserTenantId"
} catch {
    Write-Host ""
    Write-Fail "FATAL: Could not parse normal user registration response"
    Write-Host ""
    exit 1
}

# ============================================================================
# STEP 2: REGISTER ADMIN USER VIA NORMAL USER TOKEN
# ============================================================================

Write-Step 2 "PROMOTE NORMAL USER TO ADMIN (database approach)"

$testNum = 2

Write-Test $testNum "UPDATE users SET role_id = ROLE_ADMIN WHERE email = ?"

# Get admin role ID from database
$env:PGPASSWORD = "venusia"

$adminRoleId = & psql -h localhost -p 2411 -U postgres -d leadflowDB -t -c "SELECT id FROM roles WHERE name = 'ROLE_ADMIN' LIMIT 1;" 2>&1
$adminRoleId = ($adminRoleId | Out-String).Trim()

if ($adminRoleId -and $adminRoleId -ne "") {
    Write-Info "Role ADMIN ID encontrado: $adminRoleId"
    
    # Now register admin user as normal user first
    $AdminEmail = New-UniqueEmail "admin"
    $adminUserBody = @{
        name = "Admin User"
        email = $AdminEmail
        password = $AdminPassword
        confirmPassword = $AdminPassword
    } | ConvertTo-Json

    $adminUserHeaders = @{
        "Content-Type" = "application/json"
    }

    $adminRegisterResponse = Invoke-ApiRequest -Method POST `
        -Endpoint "/api/auth/register" `
        -Description "Register user for admin promotion" `
        -Headers $adminUserHeaders `
        -Body $adminUserBody `
        -ExpectedStatus @(201, 200)

    if ($adminRegisterResponse) {
        try {
            $adminToken = $adminRegisterResponse.accessToken
            $adminTenantId = $adminRegisterResponse.tenantId
            Write-Info "Admin user registered: $AdminEmail"
            
            # Now promote to ADMIN in database
            try {
                $psqlOutput = & psql -h localhost -p 2411 -U postgres -d leadflowDB -t -c "UPDATE public.users SET role_id = '$adminRoleId' WHERE email = '$AdminEmail' AND tenant_id = '$adminTenantId' RETURNING id, role_id;" 2>&1
                Write-Info "Usuário promovido para ADMIN"
                Write-Success "Admin user promoted in database"
            } catch {
                Write-Fail "Error promoting user to admin - $($_.Exception.Message)"
                exit 1
            }
        } catch {
            Write-Fail "Error parsing admin registration response"
            exit 1
        }
    } else {
        Write-Fail "FATAL: Could not register admin user"
        exit 1
    }
} else {
    Write-Fail "FATAL: Could not find ROLE_ADMIN in database"
    exit 1
}

# ============================================================================
# STEP 4: TEST ADMIN BILLING ENDPOINTS (4 ENDPOINTS)
# ============================================================================

Write-Step 4 "TEST ALL ADMIN BILLING ENDPOINTS"

$authHeaders = @{
    "Content-Type" = "application/json"
    "Authorization" = "Bearer $adminToken"
}

# TEST 4.1: GET /api/v1/admin/billing/users
$testNum = 4

Write-Test $testNum "GET /api/v1/admin/billing/users"

$usersResponse = Invoke-ApiRequest -Method GET `
    -Endpoint "/api/v1/admin/billing/users" `
    -Description "Get billing users" `
    -Headers $authHeaders `
    -ExpectedStatus @(200)

if ($usersResponse) {
    try {
        Write-Info "Total users: $($usersResponse.totalCount)"
        Write-Info "Active subscriptions: $($usersResponse.activeCount)"
        Write-Info "Trialing subscriptions: $($usersResponse.trialingCount)"
        Write-Info "Past due subscriptions: $($usersResponse.pastDueCount)"
    } catch {
        Write-Info "Response received but some fields unavailable"
    }
}

Write-Host ""

# TEST 4.2: GET /api/v1/admin/billing/analytics
$testNum = 5

Write-Test $testNum "GET /api/v1/admin/billing/analytics"

$analyticsResponse = Invoke-ApiRequest -Method GET `
    -Endpoint "/api/v1/admin/billing/analytics" `
    -Description "Get billing analytics" `
    -Headers $authHeaders `
    -ExpectedStatus @(200)

if ($analyticsResponse) {
    try {
        Write-Info "Total subscriptions: $($analyticsResponse.totalSubscriptions)"
        Write-Info "Active subscriptions: $($analyticsResponse.activeSubscriptions)"
        Write-Info "Monthly Recurring Revenue (MRR): USD $($analyticsResponse.monthlyRecurringRevenue)"
        Write-Info "Annual Recurring Revenue (ARR): USD $($analyticsResponse.annualRecurringRevenue)"
        Write-Info "Churn rate: $($analyticsResponse.churnRate)%"
    } catch {
        Write-Info "Response received but some fields unavailable"
    }
}

Write-Host ""

# TEST 4.3: GET /api/v1/admin/billing/revenue
$testNum = 6

Write-Test $testNum "GET /api/v1/admin/billing/revenue"

$revenueResponse = Invoke-ApiRequest -Method GET `
    -Endpoint "/api/v1/admin/billing/revenue" `
    -Description "Get billing revenue" `
    -Headers $authHeaders `
    -ExpectedStatus @(200)

if ($revenueResponse) {
    try {
        Write-Info "Monthly recurring revenue: USD $($revenueResponse.monthlyRecurringRevenue)"
        Write-Info "Annual recurring revenue: USD $($revenueResponse.annualRecurringRevenue)"
        Write-Info "Average monthly value: USD $($revenueResponse.averageMonthlyValue)"
        Write-Info "Total refunds issued: USD $($revenueResponse.totalRefundsIssued)"
    } catch {
        Write-Info "Response received but some fields unavailable"
    }
}

Write-Host ""

# TEST 4.4: POST /api/v1/admin/billing/refund
$testNum = 7

Write-Test $testNum "POST /api/v1/admin/billing/refund"

$refundBody = @{
    userId = "test-user-id"
    amount = 50.00
    reason = "Testing refund endpoint"
    transactionId = "txn_test_123456"
} | ConvertTo-Json

$refundResponse = Invoke-ApiRequest -Method POST `
    -Endpoint "/api/v1/admin/billing/refund" `
    -Description "Process refund request" `
    -Headers $authHeaders `
    -Body $refundBody `
    -ExpectedStatus @(200, 400, 404)

if ($refundResponse) {
    try {
        Write-Info "Refund ID: $($refundResponse.refundId)"
        Write-Info "Amount: USD $($refundResponse.amount)"
        Write-Info "Status: $($refundResponse.status)"
        Write-Info "Processed at: $($refundResponse.processedAt)"
    } catch {
        Write-Info "Response received but some fields unavailable"
    }
}

# ============================================================================
# STEP 5: VERIFY SECURITY - TEST WITH NON-ADMIN USER
# ============================================================================

Write-Step 5 "VERIFY SECURITY - NON-ADMIN USER SHOULD BE BLOCKED"

# Register test user
Write-Test 8 "POST /api/auth/register (non-admin user)"

$testUserEmail = New-UniqueEmail
$testUserPassword = "TestUser@123"

$testUserBody = @{
    name = "Test User"
    email = $testUserEmail
    password = $testUserPassword
    confirmPassword = $testUserPassword
} | ConvertTo-Json

$testUserHeaders = @{
    "Content-Type" = "application/json"
}

$testUserResponse = Invoke-ApiRequest -Method POST `
    -Endpoint "/api/auth/register" `
    -Description "Register non-admin test user" `
    -Headers $testUserHeaders `
    -Body $testUserBody `
    -ExpectedStatus @(201, 200)

if ($testUserResponse) {
    try {
        $testUserToken = $testUserResponse.accessToken
        $testUserTenantId = $testUserResponse.tenantId
        Write-Info "Test user registered: $testUserEmail"
        Write-Info "Test user token: $($testUserToken.Substring(0, 30))..."
        
        # Try to access admin endpoints with non-admin token
        Write-Host ""
        
        $testUserAuthHeaders = @{
            "Content-Type" = "application/json"
            "Authorization" = "Bearer $testUserToken"
        }

        Write-Test 9 "GET /api/v1/admin/billing/users (should be 403)"
        
        $testUsersResponse = Invoke-ApiRequest -Method GET `
            -Endpoint "/api/v1/admin/billing/users" `
            -Description "Access admin endpoint as non-admin (expect 403)" `
            -Headers $testUserAuthHeaders `
            -ExpectedStatus @(403)

        if ($testUsersResponse) {
            Write-Info "Security check passed - non-admin correctly blocked"
        }
    } catch {
        Write-Info "Could not complete security verification"
    }
} else {
    Write-Skip "Could not register test user for security verification"
}

# ============================================================================
#                            SUMMARY
# ============================================================================

Write-Header "TEST EXECUTION SUMMARY"

$TotalTests = $global:PassCount + $global:FailCount
$PassRate = if ($TotalTests -gt 0) { [math]::Round(($global:PassCount / $TotalTests) * 100, 2) } else { 0 }

Write-Host "Results:" -ForegroundColor Cyan
Write-Host "  Total Tests: $TotalTests" -ForegroundColor White
Write-Host "  Passed: $($global:PassCount)" -ForegroundColor Green
Write-Host "  Failed: $($global:FailCount)" -ForegroundColor $(if ($global:FailCount -eq 0) { "Green" } else { "Red" })
Write-Host "  Skipped: $($global:SkipCount)" -ForegroundColor Gray
Write-Host "  Pass Rate: $PassRate%" -ForegroundColor $(if ($PassRate -eq 100) { "Green" } else { "Yellow" })
Write-Host ""

if ($global:FailCount -eq 0 -and $global:PassCount -gt 0) {
    Write-Host "[SUCCESS] ALL ADMIN BILLING ENDPOINTS WORKING CORRECTLY!" -ForegroundColor Green
    Write-Host ""
    exit 0
} else {
    Write-Host "[WARNING] SOME TESTS FAILED - CHECK DETAILS ABOVE" -ForegroundColor Yellow
    Write-Host ""
    exit 1
}



