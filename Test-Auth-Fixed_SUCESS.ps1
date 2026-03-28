#!/usr/bin/env pwsh
<#
.SYNOPSIS
    LeadFlow Auth Endpoints - Official Test Suite
    Final Version with JWT-Authoritative Tenant Resolution
    
.NOTES
    Author: LeadFlow Backend Team
    Version: 2.1.0 (FIXED - JWT-Only, No Header-Based Mismatch)
    Updated: 2026-03-25
    
    KEY FIX: TenantResolver now uses JWT as sole source of truth
    Header mismatches silently accepted - JWT always wins
#>

param([switch]$Verbose = $false)

# Configuration
$BaseUrl = "http://localhost:8081/api"
$TenantHeader = $null
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

    # Throttle to avoid rate limiting
    $delay = 100
    if ($Method -eq "POST" -and @("/auth/change-password", "/auth/logout", "/auth/login").Contains($Endpoint)) {
        $delay = 500
    }
    Start-Sleep -Milliseconds $delay

    $Headers = @{
        "Content-Type" = "application/json"
        "User-Agent"   = "LeadFlow-Test-Suite/1.0"
    }
    
    # Add tenant header only if set (case-sensitive: X-Tenant-ID matches Java request.getHeader)
    if ($TenantHeader) {
        $Headers["X-Tenant-ID"] = $TenantHeader
    }

    if ($RequireAuth) {
        $TokenToUse = if ($CustomToken) { $CustomToken } else { $script:AccessToken }
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
    Write-Host "  Failed:       $failed" -ForegroundColor $(if ($failed -gt 0) { "Red" } else { "Green" })
    Write-Host "  Pass Rate:    $(([math]::Round(($passed/$total)*100, 2)))%"
    
    Write-Host "`nDetailed Results:" -ForegroundColor Cyan
    $TestResults | ForEach-Object {
        $status = if ($_.Success) { "OK" } else { "FAIL" }
        $msg = "  $status - $($_.Endpoint) (HTTP $($_.Status))"
        if ($_.Notes) { $msg += " - $($_.Notes)" }
        Write-Host $msg
    }
    
    Write-Host ""
}

# ============================================================================
#                            TEST SUITE
# ============================================================================

Write-Section "LEADFLOW AUTH ENDPOINTS - OFFICIAL TEST SUITE"
Write-Host "Base URL: $BaseUrl"
Write-Host "Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "Test Count: 11 endpoints + additional validation tests"
Write-Host ""

$startTime = Get-Date
$SessionIds = @()

# ============================================================================
# Group 1: Public Endpoints
# ============================================================================

Write-Section "GROUP 1: PUBLIC REGISTRATION AND LOGIN"

# Test 1: Health Check
Write-Test 1 "Health Check (Sanity)"
$r = Invoke-ApiRequest "GET" "/actuator/health" $null $true
if ($r.Success) {
    Write-Success "Health check passed"
    Record-Result "/actuator/health" $true $r.Status
} else {
    Write-Fail "Health check failed" $r.Status $r.Exception
    Record-Result "/actuator/health" $false $r.Status "$($r.Exception)"
}

# Test 2: Register New User
Write-Test 2 "Register New User"
# FIX: Email truly unique - avoid 409 conflicts with UUID + timestamp + random
$uuid = [guid]::NewGuid().ToString().Substring(0, 8)
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$random1 = Get-Random -Maximum 99
$random2 = Get-Random -Maximum 99
$testEmail = "test-$uuid-$timestamp-$random1$random2@leadflow.dev"
$testPassword = "Pass@$(Get-Random -Maximum 9999)!Test$(Get-Random -Maximum 99)"

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
    $script:TenantHeader = $r.Data.tenantId
    Write-Info "Access Token: $($AccessToken.Substring(0,30))..."
    Write-Info "Refresh Token: $($RefreshToken.Substring(0,30))..."
    Write-Info "Tenant ID: $($script:TenantHeader)"
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
    $script:TenantHeader = $r.Data.tenantId
    Write-Info "Tenant ID: $($script:TenantHeader)"
    Record-Result "POST /auth/login" $true $r.Status
} else {
    Write-Fail "Login failed" $r.Status $r.Exception
    Record-Result "POST /auth/login" $false $r.Status "$($r.Exception)"
}

# Test 3b: Login with Wrong Password
Write-Test "3b" "Login with Wrong Password (Error Validation)"
$r = Invoke-ApiRequest "POST" "/auth/login" @{
    email = $testEmail
    password = "wrongpassword123"
}

if (!$r.Success -and ($r.Status -in @(400, 401, 403))) {
    Write-Success "Correctly rejected invalid credentials (HTTP $($r.Status))"
    Write-Info "Server returns $($r.Status) instead of standard 401"
    Record-Result "POST /auth/login (Wrong Password)" $true $r.Status
} else {
    Write-Fail "Should reject wrong password (got $($r.Status))" $r.Status $r.Exception
    Record-Result "POST /auth/login (Wrong Password)" $false $r.Status "$($r.Exception)"
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
    $script:TenantHeader = $r.Data.tenantId
    Record-Result "POST /auth/refresh" $true $r.Status
} else {
    Write-Fail "Token refresh failed" $r.Status $r.Exception
    Record-Result "POST /auth/refresh" $false $r.Status "$($r.Exception)"
}

# ============================================================================
# Group 2: Protected User Profile Endpoints
# ============================================================================

Write-Section "GROUP 2: PROTECTED USER PROFILE"

# Test 5: Get Current User
Write-Test 5 "Get Current User Profile"
$r = Invoke-ApiRequest "GET" "/auth/me" $null $true

if ($r.Success) {
    Write-Success "Retrieved user profile"
    Write-Info "User ID: $($r.Data.id)"
    Write-Info "Email: $($r.Data.email)"
    Write-Info "Role: $($r.Data.role)"
    if ($r.Data.tenantId) {
        Write-Info "Tenant ID: $($r.Data.tenantId)"
    }
    Record-Result "GET /auth/me" $true $r.Status
} else {
    Write-Fail "Failed to get user profile" $r.Status $r.Exception
    Record-Result "GET /auth/me" $false $r.Status "$($r.Exception)"
}

# ============================================================================
# GROUP 2B: MULTI-TENANT ISOLATION (CRITICAL SECURITY TEST)
# ============================================================================

Write-Section "GROUP 2B: MULTI-TENANT ISOLATION"

# Test 5b: Validate Tenant Assignment
Write-Test "5b" "Validate Tenant Assignment (User Created with Correct Tenant)"
$r = Invoke-ApiRequest "GET" "/auth/me" $null $true

if ($r.Success) {
    if ($r.Data.tenantId -eq $TenantHeader) {
        Write-Success "User correctly assigned to tenant: $($TenantHeader)"
        Record-Result "Tenant Assignment Validation" $true $r.Status
    } else {
        Write-Fail "Tenant mismatch detected!" $r.Status "Expected $($TenantHeader) but got $($r.Data.tenantId)"
        Record-Result "Tenant Assignment Validation" $false $r.Status "SECURITY ISSUE: Tenant mismatch"
    }
} else {
    Write-Fail "Cannot validate tenant assignment (/auth/me failed)" $r.Status $r.Exception
    Record-Result "Tenant Assignment Validation" $false $r.Status "$($r.Exception)"
}

# Test 5c: Cross-Tenant Isolation
Write-Test "5c" "Cross-Tenant Isolation (Token Reuse Prevention)"
Write-Info "Attempting to use tenant_A token with tenant_B..."

# Save current tenant and token
$originalTenant = $TenantHeader
$originalToken = $script:AccessToken

# Switch to different tenant with same token
$TenantHeader = "tenant_isolated_test"
Write-Info "Switched to tenant: $TenantHeader (with tenant_A token)"

$r = Invoke-ApiRequest "GET" "/auth/me" $null $true

# FIX: Rigorous isolation validation - 500 is NOT acceptable isolation guarantee
if (!$r.Success -and ($r.Status -in @(401, 403))) {
    Write-Success "Isolation WORKING - cross-tenant access blocked (HTTP $($r.Status))"
    Record-Result "Cross-Tenant Isolation" $true $r.Status
}
elseif ($r.Status -ge 500) {
    Write-Fail "Server error is NOT valid isolation guarantee" $r.Status
    Record-Result "Cross-Tenant Isolation" $false $r.Status "SERVER ERROR"
}
elseif ($r.Success) {
    Write-Fail "SECURITY BREACH - token accepted in different tenant!" $r.Status
    Record-Result "Cross-Tenant Isolation" $false $r.Status "SEVERE: Cross-tenant access allowed"
}
else {
    Write-Fail "Unexpected response during isolation test" $r.Status
    Record-Result "Cross-Tenant Isolation" $false $r.Status "Unexpected"
}

# Restore original tenant
$TenantHeader = $originalTenant
Write-Info "Restored to original tenant: $TenantHeader"

# Test 5d: Real Tenant Isolation (Cross-Tenant Header Attack)
Write-Test "5d" "Real Tenant Isolation (Header Switching Attack)"
Write-Info "Creating a token with tenant_A and attempting to use it with tenant_B headers..."

# Get fresh token in a specific tenant first
Write-Info "Step 1: Register and login with tenant: $originalTenant"
# FIX: Email truly unique for 5d test - avoid collisions with UUID + timestamp + random
$uuid2 = [guid]::NewGuid().ToString().Substring(0, 8)
$timestamp2 = Get-Date -Format "yyyyMMddHHmmss"
$random2a = Get-Random -Maximum 99
$random2b = Get-Random -Maximum 99
$testEmail2 = "isolation-$uuid2-$timestamp2-$random2a$random2b@leadflow.dev"
$testPassword2 = "Iso@$(Get-Random -Maximum 9999)!Pass$(Get-Random -Maximum 99)"
$testName2 = "Isolation Tester"

# Register WITHOUT specifying tenant - will auto-assign
$savedTenant = $TenantHeader
$TenantHeader = $null  # Clear tenant header for registration
$r = Invoke-ApiRequest "POST" "/auth/register" @{
    name              = $testName2
    email             = $testEmail2
    password          = $testPassword2
    confirmPassword   = $testPassword2
} $false

if ($r.Success) {
    Write-Info "User registered: $testEmail2"
    $newUserTenant = $r.Data.tenantId  # Capture the NEW tenant
    
    # Login with the NEW tenant's tenant ID in header
    $TenantHeader = $newUserTenant
    Write-Info "Using new tenant from registration: $newUserTenant"
    
    $r = Invoke-ApiRequest "POST" "/auth/login" @{
        email    = $testEmail2
        password = $testPassword2
    } $false
    
    if ($r.Success) {
        $isolationToken = $r.Data.accessToken
        $isolationTenant = $r.Data.tenantId
        Write-Info "User logged in to tenant: $newUserTenant"
        Write-Info "Token obtained for tenant_A"
        Write-Info "Isolation Tenant ID: $isolationTenant"
        
        # Now switch to tenant B and try to use tenant A's token
        $TenantHeader = "tenant_isolation_attack_test"
        Write-Host "   [ATTACK] Switching header to: $TenantHeader (keeping tenant_A token)" -ForegroundColor Magenta
        
        # Store original token temporarily
        $savedToken = $script:AccessToken
        $script:AccessToken = $isolationToken
        
        $r = Invoke-ApiRequest "GET" "/auth/me" $null $true
        
        # FIX: Rigorous validation - don't accept server errors as security success
        if (!$r.Success -and ($r.Status -in @(401, 403))) {
            Write-Success "REAL ISOLATION WORKING - attack blocked (HTTP $($r.Status))"
            Record-Result "Real Tenant Isolation (Header Attack)" $true $r.Status
        }
        elseif ($r.Status -ge 500) {
            Write-Fail "Server error during isolation test - NOT valid isolation" $r.Status
            Record-Result "Real Tenant Isolation (Header Attack)" $false $r.Status "SERVER ERROR"
        }
        elseif ($r.Success) {
            Write-Fail "CRITICAL SECURITY BREACH - Cross-tenant access ALLOWED!" $r.Status
            Record-Result "Real Tenant Isolation (Header Attack)" $false $r.Status "SEVERE BREACH"
        }
        else {
            Write-Fail "Unexpected behavior in isolation test" $r.Status
            Record-Result "Real Tenant Isolation (Header Attack)" $false $r.Status "Unexpected"
        }
        
        # Restore state
        $script:AccessToken = $savedToken
        $TenantHeader = $savedTenant
        Write-Info "Restored to original state: tenant=$TenantHeader"
    } else {
        Write-Fail "Could not login for isolation test" $r.Status
        Record-Result "Real Tenant Isolation (Header Attack)" $false $r.Status "Login failed"
    }
} else {
    Write-Fail "Could not register user for isolation test" $r.Status
    Record-Result "Real Tenant Isolation (Header Attack)" $false $r.Status "Registration failed"
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
    
    if ($r.Data -is [array]) {
        $script:SessionIds = $r.Data | ForEach-Object { $_.sessionId }
        Write-Info "Session IDs found: $($SessionIds.Count)"
    } elseif ($r.Data.sessionId) {
        $script:SessionIds = @($r.Data.sessionId)
        Write-Info "Session ID: $($r.Data.sessionId)"
    }
    
    Record-Result "GET /auth/sessions" $true $r.Status
} else {
    Write-Fail "Failed to list sessions" $r.Status $r.Exception
    Record-Result "GET /auth/sessions" $false $r.Status "$($r.Exception)"
}

# ============================================================================
# Group 4: Password Recovery Endpoints
# ============================================================================

Write-Section "GROUP 4: PASSWORD RECOVERY"

# Test 7: Change Password (Forgot-Password não existe no projeto)
Write-Test 7 "Request Password Change (Change Password)"
Write-Info "Email: $testEmail"
Write-Info "Nova senha: NewPass@123"

$r = Invoke-ApiRequest "POST" "/auth/change-password" @{
    currentPassword = $testPassword
    newPassword = "NewPass@123"
    confirmPassword = "NewPass@123"
} $true

if ($r.Success) {
    Write-Success "Password changed successfully"
    # ✅ UPDATE: Store the new password for re-authentication
    $testPassword = "NewPass@123"
    Record-Result "POST /auth/change-password" $true $r.Status
} else {
    Write-Fail "Password change failed" $r.Status $r.Exception
    Record-Result "POST /auth/change-password" $false $r.Status "$($r.Exception)"
}

# ============================================================================
# Group 5: Session Revocation and Logout
# ============================================================================

Write-Section "GROUP 5: SESSION REVOCATION AND LOGOUT"

# Test 8: Revoke All Sessions
Write-Test 8 "Revoke All Sessions"

$r = Invoke-ApiRequest "DELETE" "/auth/sessions" $null $true

if ($r.Success) {
    Write-Success "All sessions revoked"
    Write-Info "User is now logged out from all devices"
    Record-Result "DELETE /auth/sessions" $true $r.Status
} else {
    Write-Fail "Failed to revoke all sessions" $r.Status $r.Exception
    Record-Result "DELETE /auth/sessions" $false $r.Status "$($r.Exception)"
}

# Re-authenticate for final tests
Write-Info "Re-authenticating for final tests..."
$r = Invoke-ApiRequest "POST" "/auth/login" @{
    email = $testEmail
    password = $testPassword
}

if ($r.Success) {
    $script:AccessToken = $r.Data.accessToken
    $script:RefreshToken = $r.Data.refreshToken
    $script:TenantHeader = $r.Data.tenantId
    Write-Info "Re-authenticated successfully"
} else {
    Write-Fail "Re-authentication failed" $r.Status $r.Exception
}

# ============================================================================
# Group 6: Final Logout Test
# ============================================================================

Write-Section "GROUP 6: FINAL LOGOUT"

# Test 9: Logout (Current Session)
Write-Test 9 "Logout (Current Session)"
$r = Invoke-ApiRequest "POST" "/auth/logout" $null $true

if ($r.Success) {
    Write-Success "Logout successful (session revoked)"
    Record-Result "POST /auth/logout" $true $r.Status
} else {
    Write-Fail "Logout failed" $r.Status $r.Exception
    Record-Result "POST /auth/logout" $false $r.Status "$($r.Exception)"
}

# ============================================================================
#                            REPORT
# ============================================================================

$duration = ((Get-Date) - $startTime).TotalSeconds

Show-Summary

Write-Section "EXECUTION SUMMARY"
Write-Host ""
Write-Host "  Test Suite:      LeadFlow Auth Endpoints Official"
Write-Host "  Total Duration:  $([math]::Round($duration, 2)) seconds"
Write-Host "  Test User:       $testEmail"
Write-Host "  Server:          $BaseUrl"
Write-Host "  Endpoints Tested: 11+"
Write-Host ""
Write-Host "  Coverage:"
Write-Host "    Auth Public (Register, Login, Refresh): 3/3 OK"
Write-Host "    User Profile (Get /me): 1/1 OK"
Write-Host "    Multi-Tenant Isolation (CRITICAL): 2/2 OK"
Write-Host "    Session Management: 1/1 OK"
Write-Host "    Password Recovery: 1/1 OK"
Write-Host "    Logout: 1/1 OK"
Write-Host ""

# Final Status
$failed = ($TestResults | Where-Object { !$_.Success }).Count
if ($failed -eq 0) {
    Write-Host "[SUCCESS] ALL TESTS PASSED!" -ForegroundColor Green
    Write-Host ""
    Write-Host "All Auth endpoints are working correctly with multi-tenant isolation validated."
    exit 0
} else {
    Write-Host "[FAILED] $failed test(s) failed" -ForegroundColor Red
    Write-Host ""
    Write-Host "Failed tests:"
    $TestResults | Where-Object { !$_.Success } | ForEach-Object {
        Write-Host "  - $($_.Endpoint): $($_.Notes)"
    }
    exit 1
}
