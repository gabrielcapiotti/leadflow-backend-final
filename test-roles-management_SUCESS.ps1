#Requires -Version 5.0
<#
.SYNOPSIS
    Roles Management Test Suite
.DESCRIPTION
    Tests for roles endpoints in RoleController:
    - GET /api/roles - List all roles (Admin-only)
    - GET /api/roles/{id} - Get role detail (Admin-only)
    
    Note: Create/Update/Delete not implemented in current version
.NOTES
    Author: LeadFlow Test Suite
    Version: 2.0
    Last Updated: 2026-03-30
#>

param(
    [string]$BaseUrl = "http://localhost:8081",
    [string]$Username = "roles_test@e2e.com",
    [string]$Password = "RolesTest123!@#"
)

# Configuration
$ErrorActionPreference = "SilentlyContinue"
$global:TotalTests = 0
$global:PassedTests = 0
$global:FailedTests = 0
$global:UserToken = ""
$global:TenantId = ""

$Green = "Green"
$Red = "Red"
$Yellow = "Yellow"
$Cyan = "Cyan"
$DarkGray = "DarkGray"

# Helper Functions
function Write-Success { param([string]$Msg, [int]$Code = 200); Write-Host "✅ OK - $Msg (HTTP $Code)" -ForegroundColor $Green; $global:PassedTests++ }
function Write-Fail { param([string]$Msg, [int]$Code = 0); Write-Host "❌ FAIL - $Msg (HTTP $Code)" -ForegroundColor $Red; $global:FailedTests++ }
function Write-Info { param([string]$Msg); Write-Host "   [INFO] $Msg" -ForegroundColor $DarkGray }
function Write-Step { param([int]$N, [string]$Title); Write-Host "`n[TEST $N] $Title" -ForegroundColor $Yellow; $global:TotalTests++ }
function Write-Header { param([string]$Title); Write-Host "`n`n━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor $Cyan; Write-Host $Title -ForegroundColor $Cyan; Write-Host "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━" -ForegroundColor $Cyan }

# Header
Write-Host ""
Write-Host "========================================" -ForegroundColor $Cyan
Write-Host " ROLES MANAGEMENT TEST SUITE" -ForegroundColor $Cyan
Write-Host "========================================" -ForegroundColor $Cyan
Write-Host ""

# ===== STEP 1: REGISTER USER =====
Write-Header "STEP 1: USER REGISTRATION"

Write-Step 1 "Register Regular User"
try {
    $resp = Invoke-WebRequest -Uri "$BaseUrl/api/auth/register" -Method POST -Body (@{
        email = $Username; password = $Password; confirmPassword = $Password; name = "Test User"
    } | ConvertTo-Json) -Headers @{"Content-Type" = "application/json"} -UseBasicParsing -ErrorAction Stop
    
    $data = $resp.Content | ConvertFrom-Json
    $global:TenantId = $data.tenantId
    Write-Success "User registered" $resp.StatusCode
    Write-Info "TenantId: $global:TenantId"
} catch {
    Write-Fail "Registration" $_.Exception.Response.StatusCode.value__
    exit 1
}

# ===== STEP 2: LOGIN =====
Write-Header "STEP 2: USER LOGIN"

Write-Step 2 "Login User"
try {
    $resp = Invoke-WebRequest -Uri "$BaseUrl/api/auth/login" -Method POST -Body (@{
        email = $Username; password = $Password; tenantId = $global:TenantId
    } | ConvertTo-Json) -Headers @{"Content-Type" = "application/json"} -UseBasicParsing -ErrorAction Stop
    
    $global:UserToken = ($resp.Content | ConvertFrom-Json).accessToken
    Write-Success "User authenticated" $resp.StatusCode
    Write-Info "Token: $($global:UserToken.Substring(0,25))..."
} catch {
    Write-Fail "Login" $_.Exception.Response.StatusCode.value__
    exit 1
}

# ===== STEP 3: ROLES ENDPOINTS =====
Write-Header "STEP 3: ROLES ENDPOINTS (ADMIN-ONLY)"

$headers = @{ "Authorization" = "Bearer $global:UserToken"; "Content-Type" = "application/json" }

# TEST 3: GET /api/roles as regular user (should fail 403)
Write-Step 3 "GET /api/roles - Regular User (Should Fail 403)"
Write-Info "Using token from login: $($global:UserToken.Substring(0,40))..."
try {
    Write-Info "Making request with Authorization: Bearer $($global:UserToken.Substring(0,40))..."
    $resp = Invoke-WebRequest -Uri "$BaseUrl/api/roles" -Method Get -Headers $headers -UseBasicParsing -ErrorAction Stop
    Write-Fail "Regular user should NOT access roles list" $resp.StatusCode
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    $statusDescription = $_.Exception.Response.StatusDescription
    Write-Info "Response code: $code, Description: $statusDescription"
    
    # Log full response body
    try {
        $errorBody = $_.Exception.Response.GetResponseStream()
        $reader = New-Object System.IO.StreamReader($errorBody)
        $errorText = $reader.ReadToEnd()
        Write-Info "Response body: $errorText"
    } catch { }
    
    if ($code -eq 403) {
        Write-Success "Regular user correctly blocked" 403
        Write-Info "@PreAuthorize('hasRole(ADMIN)') is working"
    } else {
        Write-Fail "Unexpected response" $code
    }
}

# TEST 4: GET /api/roles/{id} as regular user (should fail 403)
Write-Step 4 "GET /api/roles/{id} - Regular User (Should Fail 403)"
try {
    $resp = Invoke-WebRequest -Uri "$BaseUrl/api/roles/00000000-0000-0000-0000-000000000001" -Method Get -Headers $headers -UseBasicParsing -ErrorAction Stop
    Write-Fail "Regular user should NOT access role detail" $resp.StatusCode
} catch {
    $code = $_.Exception.Response.StatusCode.value__
   if ($code -eq 403) {
        Write-Success "Regular user correctly blocked" 403
        Write-Info "Authorization checked before validation"
    } else {
        Write-Fail "Unexpected response" $code
    }
}

# ===== STEP 4: SECURITY TESTS =====
Write-Header "STEP 4: SECURITY VALIDATION"

# TEST 5: Missing auth header
Write-Step 5 "Missing Authorization Header"
try {
    $resp = Invoke-WebRequest -Uri "$BaseUrl/api/roles" -Method Get -Headers @{"Content-Type" = "application/json"} -UseBasicParsing -ErrorAction Stop
    Write-Fail "Should reject missing auth" $resp.StatusCode
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 401) {
        Write-Success "Correctly rejected (401)" 401
    } else {
        Write-Fail "Unexpected" $code
    }
}

# TEST 6: Invalid token
Write-Step 6 "Invalid Authorization Token"
try {
    $resp = Invoke-WebRequest -Uri "$BaseUrl/api/roles" -Method Get -Headers @{"Authorization" = "Bearer invalid.token"; "Content-Type" = "application/json"} -UseBasicParsing -ErrorAction Stop
    Write-Fail "Should reject invalid token" $resp.StatusCode
} catch {
    $code = $_.Exception.Response.StatusCode.value__
    if ($code -eq 401) {
        Write-Success "Correctly rejected (401)" 401
    } else {
        Write-Fail "Unexpected" $code
    }
}

# ===== FINAL REPORT =====
Write-Header "FINAL REPORT"

$rate = if ($global:TotalTests -gt 0) { [Math]::Round(($global:PassedTests / $global:TotalTests) * 100, 1) } else { 0 }

Write-Host ""
Write-Host "  Total Tests: $global:TotalTests" -ForegroundColor $Cyan
Write-Host "  Passed: $global:PassedTests" -ForegroundColor $Green
Write-Host "  Failed: $global:FailedTests" -ForegroundColor $Red
Write-Host "  Pass Rate: $rate%" -ForegroundColor (if ($rate -ge 80) { $Green } else { $Yellow })

Write-Host ""
Write-Host "FINAL REPORT" -ForegroundColor $Cyan
Write-Host ""

$rate = if ($global:TotalTests -gt 0) { [Math]::Round(($global:PassedTests / $global:TotalTests) * 100, 1) } else { 0 }

Write-Host "Total Tests: $global:TotalTests" -ForegroundColor $Cyan
Write-Host "Passed: $global:PassedTests" -ForegroundColor $Green
Write-Host "Failed: $global:FailedTests" -ForegroundColor $Red
Write-Host "Pass Rate: $rate%" -ForegroundColor (if ($rate -ge 80) { $Green } else { $Yellow })
