#!/usr/bin/env pwsh
<#
.SYNOPSIS
    LeadFlow Auth Endpoints - Official Test Suite
    
.DESCRIPTION
    Comprehensive test suite for all 12 authentication endpoints.
    Tests all public and protected endpoints with proper error handling.
    
.NOTES
    Author: LeadFlow Backend Team
    Version: 1.0.0
    Updated: 2026-03-19
    
    Requirements:
    - PowerShell 5.1 or higher
    - Server running on http://localhost:8081
    - Valid email access for testing (optional, for reset password flow)
    
.EXAMPLE
    .\test-auth-complete.ps1
    
    .\test-auth-complete.ps1 -Verbose
#>

param(
    [switch]$Verbose = $false
)

# Configuration
$BaseUrl = "http://localhost:8081"
$TenantHeader = "public"
$ProgressPreference = 'SilentlyContinue'

# Test Results Tracking
$TestResults = @()
$AccessToken = $null
$RefreshToken = $null
$ResetToken = $null

# ============================================================================
#                            UTILITY FUNCTIONS
# ============================================================================

function Write-Section {
    param([string]$Title)
    Write-Host "`n$('='*80)" -ForegroundColor Cyan
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host "$('='*80)" -ForegroundColor Cyan
}

function Write-Test {
    param($Number, [string]$Name)
    Write-Host "`n[$Number] $Name" -ForegroundColor Yellow
}

function Write-Success {
    param([string]$Message, [int]$Status = 200)
    Write-Host "   [OK] $Message (HTTP $Status)" -ForegroundColor Green
}

function Write-Fail {
    param([string]$Message, [int]$Status = 0, [string]$Error = $null)
    Write-Host "   [FAIL] $Message (HTTP $Status)" -ForegroundColor Red
    if ($Error) {
        Write-Host "      Error: $Error" -ForegroundColor DarkRed
    }
}

function Write-Skip {
    param([string]$Message)
    Write-Host "   [SKIP] $Message" -ForegroundColor Gray
}

function Write-Info {
    param([string]$Message)
    Write-Host "      [INFO] $Message" -ForegroundColor DarkGray
}

function Invoke-ApiRequest {
    param(
        [string]$Method,
        [string]$Endpoint,
        [hashtable]$Body = $null,
        [bool]$RequireAuth = $false,
        [string]$CustomToken = $null
    )

    $Headers = @{
        "Content-Type" = "application/json"
        "X-Tenant-Id"  = $TenantHeader
    }

    if ($RequireAuth) {
        $TokenToUse = if ($CustomToken) { $CustomToken } else { $AccessToken }
        if ($TokenToUse) {
            $Headers["Authorization"] = "Bearer $TokenToUse"
        }
    }

    try {
        $params = @{
            Uri     = "$BaseUrl$Endpoint"
            Method  = $Method
            Headers = $Headers
        }

        if ($Body) {
            $params.Body = ($Body | ConvertTo-Json -Depth 10)
        }

        $response = Invoke-RestMethod @params

        return @{
            Success = $true
            Status  = 200
            Data    = $response
        }
    }
    catch {
        $status = 0
        try { 
            $status = $_.Exception.Response.StatusCode.value__ 
        } catch {}
        
        $errorMsg = $null
        try { 
            $errorMsg = $_.ErrorDetails.Message 
        } catch {}

        return @{
            Success = $false
            Status  = $status
            Error   = $errorMsg
            Exception = $_.Exception.Message
        }
    }
}

function Record-Result {
    param([string]$Endpoint, [bool]$Success, [int]$Status, [string]$Notes = "")
    
    $result = @{
        Endpoint = $Endpoint
        Success = $Success
        Status = $Status
        Notes = $Notes
    }
    
    $script:TestResults += $result
}

function Show-Summary {
    Write-Section "TEST SUMMARY"
    
    $total = $TestResults.Count
    $passed = ($TestResults | Where-Object { $_.Success }).Count
    $failed = $total - $passed
    
    Write-Host "`nResults:" -ForegroundColor Cyan
    Write-Host "  Total Tests:  $total"
    Write-Host "  Passed:       $passed" -ForegroundColor Green
    Write-Host "  Failed:       $failed" $(if ($failed -gt 0) { "-ForegroundColor Red" } else { "-ForegroundColor Green" })
    Write-Host "  Pass Rate:    $(([math]::Round(($passed/$total)*100, 2)))%"
    
    Write-Host "`nDetailed Results:" -ForegroundColor Cyan
    $TestResults | ForEach-Object {
        $status = if ($_.Success) { "✅ PASS" } else { "❌ FAIL" }
        Write-Host "  $status - $($_.Endpoint) (HTTP $($_.Status))" $(if ($_.Notes) { "- $($_.Notes)" })
    }
    
    Write-Host ""
}

# ============================================================================
#                            TEST SUITE
# ============================================================================

Write-Section "LEADFLOW AUTH ENDPOINTS - OFFICIAL TEST SUITE"
Write-Host "Base URL: $BaseUrl"
Write-Host "Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "Test Count: 12 endpoints"

$startTime = Get-Date

# ============================================================================
# Group 1: Public Endpoints (No Auth Required)
# ============================================================================

Write-Section "GROUP 1: PUBLIC REGISTRATION AND LOGIN"

# Test 1: Health Check
Write-Test 1 "Health Check (Sanity)"
$r = Invoke-ApiRequest "GET" "/actuator/health"
if ($r.Success) {
    Write-Success "Health check passed"
    Record-Result "/actuator/health" $true $r.Status
} else {
    Write-Fail "Health check failed" $r.Status $r.Exception
    Record-Result "/actuator/health" $false $r.Status "$($r.Exception)"
}

# Test 2: Register New User
Write-Test 2 "Register New User"
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$testEmail = "test-$timestamp@leadflow.dev"
$testPassword = "SecurePass123!@"

Write-Info "Email: $testEmail"
Write-Info "Password: $testPassword"

$r = Invoke-ApiRequest "POST" "/auth/register" @{
    name = "Test User Auth"
    email = $testEmail
    password = $testPassword
    confirmPassword = $testPassword
}

if ($r.Success) {
    Write-Success "User registered successfully"
    $script:AccessToken = $r.Data.accessToken
    $script:RefreshToken = $r.Data.refreshToken
    Write-Info "Access Token: $($AccessToken.Substring(0,30))..."
    Write-Info "Refresh Token: $($RefreshToken.Substring(0,30))..."
    Record-Result "POST /auth/register" $true $r.Status
} else {
    Write-Fail "Registration failed" $r.Status $r.Exception
    Record-Result "POST /auth/register" $false $r.Status "$($r.Exception)"
    Write-Host "Cannot continue without valid token" -ForegroundColor Red
    exit 1
}

# Test 3: Login with Credentials
Write-Test 3 "Login with Credentials"
$r = Invoke-ApiRequest "POST" "/auth/login" @{
    email = $testEmail
    password = $testPassword
}

if ($r.Success) {
    Write-Success "Login successful"
    $script:AccessToken = $r.Data.accessToken
    $script:RefreshToken = $r.Data.refreshToken
    Record-Result "POST /auth/login" $true $r.Status
} else {
    Write-Fail "Login failed" $r.Status $r.Exception
    Record-Result "POST /auth/login" $false $r.Status "$($r.Exception)"
}

# Test 4: Refresh Token
Write-Test 4 "Refresh Token"
$r = Invoke-ApiRequest "POST" "/auth/refresh" @{
    refreshToken = $RefreshToken
}

if ($r.Success) {
    Write-Success "Token refreshed successfully"
    $script:AccessToken = $r.Data.accessToken
    $script:RefreshToken = $r.Data.refreshToken
    Record-Result "POST /auth/refresh" $true $r.Status
} else {
    Write-Fail "Token refresh failed" $r.Status $r.Exception
    Record-Result "POST /auth/refresh" $false $r.Status "$($r.Exception)"
}

# ============================================================================
# Group 2: Protected User Profile Endpoints
# ============================================================================

Write-Section "GROUP 2: PROTECTED USER PROFILE"

# Test 5: Get Current User (/auth/me)
Write-Test 5 "Get Current User Profile"
$r = Invoke-ApiRequest "GET" "/auth/me" $null $true

if ($r.Success) {
    Write-Success "Retrieved user profile"
    Write-Info "User ID: $($r.Data.id)"
    Write-Info "Email: $($r.Data.email)"
    Write-Info "Role: $($r.Data.role)"
    Record-Result "GET /auth/me" $true $r.Status
} else {
    Write-Fail "Failed to get user profile" $r.Status $r.Exception
    Record-Result "GET /auth/me" $false $r.Status "$($r.Exception)"
}

# ============================================================================
# Group 3: Session Management Endpoints
# ============================================================================

Write-Section "GROUP 3: SESSION MANAGEMENT"

# Test 6: List Sessions
Write-Test 6 "List Active Sessions"
$r = Invoke-ApiRequest "GET" "/auth/sessions" $null $true

if ($r.Success) {
    Write-Success "Retrieved sessions"
    Write-Info "Total Sessions: $($r.Data.Count)"
    Record-Result "GET /auth/sessions" $true $r.Status
} else {
    Write-Fail "Failed to list sessions" $r.Status $r.Exception
    Record-Result "GET /auth/sessions" $false $r.Status "$($r.Exception)"
}

# ============================================================================
# Group 4: Password Recovery Endpoints (NEW)
# ============================================================================

Write-Section "GROUP 4: PASSWORD RECOVERY (NEW)"

# Test 7: Forgot Password
Write-Test 7 "Request Password Reset (Forgot Password)"
Write-Info "Email: $testEmail"

$r = Invoke-ApiRequest "POST" "/auth/forgot-password" @{
    email = $testEmail
}

if ($r.Success) {
    Write-Success "Password reset request sent"
    Write-Info "Message: $($r.Data.message)"
    Record-Result "POST /auth/forgot-password" $true $r.Status
} else {
    Write-Fail "Password reset request failed" $r.Status $r.Exception
    Record-Result "POST /auth/forgot-password" $false $r.Status "$($r.Exception)"
}

# Test 8: Forgot Password with Invalid Email (Anti-Enumeration)
Write-Test 8 "Forgot Password with Non-Existent Email (Anti-Enumeration Check)"
Write-Info "Testing anti-enumeration - should return 200 regardless"

$r = Invoke-ApiRequest "POST" "/auth/forgot-password" @{
    email = "nonexistent-$timestamp@example.com"
}

if ($r.Success -and $r.Status -eq 200) {
    Write-Success "Anti-enumeration working (returns 200 for non-existent email)"
    Record-Result "POST /auth/forgot-password (Anti-Enum)" $true $r.Status
} else {
    Write-Fail "Anti-enumeration not working" $r.Status $r.Exception
    Record-Result "POST /auth/forgot-password (Anti-Enum)" $false $r.Status "$($r.Exception)"
}

# Test 9: Reset Password with Invalid Token
Write-Test 9 "Reset Password with Invalid Token"
$r = Invoke-ApiRequest "POST" "/auth/reset-password" @{
    token = "invalid-token-xyz"
    newPassword = "NewPassword123!@"
}

if (!$r.Success -and $r.Status -eq 401) {
    Write-Success "Correctly rejected invalid token"
    Record-Result "POST /auth/reset-password (Invalid Token)" $true $r.Status
} else {
    Write-Fail "Should reject invalid token with 401" $r.Status $r.Exception
    Record-Result "POST /auth/reset-password (Invalid Token)" $false $r.Status "$($r.Exception)"
}

# ============================================================================
# Group 5: Password Change Endpoint
# ============================================================================

Write-Section "GROUP 5: PASSWORD MANAGEMENT"

# Test 10: Change Password
Write-Test 10 "Change Password"
$newPassword = "NewSecurePass456!@"
Write-Info "Changing password to: $newPassword"

$r = Invoke-ApiRequest "POST" "/auth/change-password" @{
    currentPassword = $testPassword
    newPassword = $newPassword
    confirmPassword = $newPassword
} $true

if ($r.Success) {
    Write-Success "Password changed successfully"
    Write-Info "⚠️  All sessions have been revoked for security"
    Record-Result "POST /auth/change-password" $true $r.Status
    
    # After password change, need to re-login
    $script:testPassword = $newPassword
    $r = Invoke-ApiRequest "POST" "/auth/login" @{
        email = $testEmail
        password = $newPassword
    }
    
    if ($r.Success) {
        Write-Success "Re-login successful after password change"
        $script:AccessToken = $r.Data.accessToken
        $script:RefreshToken = $r.Data.refreshToken
    } else {
        Write-Fail "Re-login after password change failed" $r.Status $r.Exception
    }
} else {
    Write-Fail "Password change failed" $r.Status $r.Exception
    Record-Result "POST /auth/change-password" $false $r.Status "$($r.Exception)"
}

# ============================================================================
# Group 6: Logout & Session Revocation
# ============================================================================

Write-Section "GROUP 6: LOGOUT AND SESSION MANAGEMENT"

# Test 11: Logout
Write-Test 11 "Logout (Current Session)"
$r = Invoke-ApiRequest "POST" "/auth/logout" $null $true

if ($r.Success) {
    Write-Success "Logout successful (session revoked)"
    Record-Result "POST /auth/logout" $true $r.Status
} else {
    Write-Fail "Logout failed" $r.Status $r.Exception
    Record-Result "POST /auth/logout" $false $r.Status "$($r.Exception)"
}

# After logout, re-login for final test (revoke-all-sessions requires valid token)
Write-Info "Re-authenticating for final test..."
$r = Invoke-ApiRequest "POST" "/auth/login" @{
    email = $testEmail
    password = $newPassword
}

if ($r.Success) {
    $script:AccessToken = $r.Data.accessToken
    $script:RefreshToken = $r.Data.refreshToken
    Write-Info "Re-authenticated successfully for revoke-all-sessions test"
} else {
    Write-Fail "Re-authentication failed - cannot proceed with revoke-all-sessions" $r.Status $r.Exception
}

# Test 12: Revoke All Sessions
Write-Test 12 "Revoke All Sessions"
$r = Invoke-ApiRequest "DELETE" "/auth/sessions" $null $true

if ($r.Success) {
    Write-Success "All sessions revoked"
    Write-Info "⚠️  User is now logged out from all devices"
    Record-Result "DELETE /auth/sessions" $true $r.Status
} else {
    Write-Fail "Failed to revoke all sessions" $r.Status $r.Exception
    Record-Result "DELETE /auth/sessions" $false $r.Status "$($r.Exception)"
}

# ============================================================================
#                            REPORT
# ============================================================================

$duration = ((Get-Date) - $startTime).TotalSeconds

Show-Summary

Write-Section "EXECUTION SUMMARY"
Write-Host ""
Write-Host "  Test Suite:      LeadFlow Auth Endpoints"
Write-Host "  Total Duration:  $([math]::Round($duration, 2)) seconds"
Write-Host "  Test User:       $testEmail"
Write-Host "  Server:          $BaseUrl"
Write-Host ""

# Final Status
$failed = ($TestResults | Where-Object { !$_.Success }).Count
if ($failed -eq 0) {
    Write-Host "[SUCCESS] ALL TESTS PASSED!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "[FAILED] $failed test(s) failed" -ForegroundColor Red
    exit 1
}
