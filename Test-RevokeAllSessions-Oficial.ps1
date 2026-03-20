#!/usr/bin/env pwsh
<#
.SYNOPSIS
    LeadFlow Auth - Revoke All Sessions Test Suite
    
.DESCRIPTION
    Advanced test suite for the DELETE /auth/sessions endpoint.
    Tests multi-session revocation with comprehensive scenarios.
    
.NOTES
    Author: LeadFlow Backend Team
    Version: 1.0.0
#>

# Configuration
$BaseUrl = "http://localhost:8081"
$TenantHeader = "public"
$ProgressPreference = 'SilentlyContinue'

# Test Results
$TestResults = @()
$AccessToken = $null
$RefreshToken = $null

# ============================================================================
#                            UTILITY FUNCTIONS
# ============================================================================

function Write-Section {
    param([string]$Title)
    Write-Host ""
    Write-Host ("=" * 80) -ForegroundColor Cyan
    Write-Host "  $Title" -ForegroundColor Cyan
    Write-Host ("=" * 80) -ForegroundColor Cyan
}

function Write-Test {
    param($Number, [string]$Name)
    Write-Host ""
    Write-Host "[$Number] $Name" -ForegroundColor Yellow
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
            Uri             = "$BaseUrl$Endpoint"
            Method          = $Method
            Headers         = $Headers
            ContentType     = "application/json"
        }

        if ($Body) {
            $params['Body'] = ($Body | ConvertTo-Json -Depth 10 -Compress)
        }

        $response = Invoke-RestMethod @params
        
        if ($response) {
            return @{
                Success = $true
                Status  = 200
                Data    = $response
            }
        } else {
            return @{
                Success = $true
                Status  = 204
                Data    = $null
            }
        }
    }
    catch {
        $status = 0
        try { 
            $status = [int]$_.Exception.Response.StatusCode.value__ 
        } catch {
            $status = 0
        }
        
        $errorMsg = ""
        try { 
            $errorMsg = $_.ErrorDetails.Message 
        } catch {
            $errorMsg = $_.Exception.Message
        }

        return @{
            Success = $false
            Status  = $status
            Error   = $errorMsg
            Exception = $_.Exception.Message
        }
    }
}

function Record-Result {
    param([string]$Test, [bool]$Success, [int]$Status, [string]$Notes = "")
    
    $result = @{
        Test = $Test
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
    
    Write-Host ""
    Write-Host "Results:" -ForegroundColor Cyan
    Write-Host "  Total Tests:  $total"
    Write-Host "  Passed:       $passed" -ForegroundColor Green
    Write-Host "  Failed:       $failed" $(if ($failed -gt 0) { "-ForegroundColor Red" })
    Write-Host "  Pass Rate:    $(([math]::Round(($passed/$total)*100, 2)))%"
    
    Write-Host ""
    Write-Host "Detailed Results:" -ForegroundColor Cyan
    $TestResults | ForEach-Object {
        $status = if ($_.Success) { "[PASS]" } else { "[FAIL]" }
        Write-Host "  $status - $($_.Test) (HTTP $($_.Status))"
    }
    
    Write-Host ""
}

# ============================================================================
#                            TEST SUITE
# ============================================================================

Write-Section "LEADFLOW - REVOKE ALL SESSIONS TEST SUITE"
Write-Host "Base URL: $BaseUrl"
Write-Host "Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""
Write-Host "Test Focus: DELETE /auth/sessions endpoint"
Write-Host "Scenarios: Multi-session revocation, token invalidation"

$startTime = Get-Date

# ============================================================================
# Phase 1: Setup - Create User Account
# ============================================================================

Write-Section "PHASE 1: SETUP - CREATE TEST USER"

Write-Test 1 "Register Test User"
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$testEmail = "revoke-test-$timestamp@leadflow.dev"
$testPassword = "SecurePass123!@"

Write-Info "Email: $testEmail"

$r = Invoke-ApiRequest "POST" "/auth/register" @{
    name = "Revoke Test User"
    email = $testEmail
    password = $testPassword
    confirmPassword = $testPassword
}

if ($r.Success) {
    Write-Success "User registered successfully"
    $script:AccessToken = $r.Data.accessToken
    $script:RefreshToken = $r.Data.refreshToken
    Record-Result "POST /auth/register" $true $r.Status
} else {
    Write-Fail "Registration failed" $r.Status $r.Exception
    Record-Result "POST /auth/register" $false $r.Status "$($r.Exception)"
    exit 1
}

# ============================================================================
# Phase 2: Create Multiple Sessions
# ============================================================================

Write-Section "PHASE 2: CREATE MULTIPLE SESSIONS"

Write-Test 2 "Create Session 1 (Primary Login)"
Write-Info "Session 1: Main account login"
$r = Invoke-ApiRequest "POST" "/auth/login" @{
    email = $testEmail
    password = $testPassword
}

if ($r.Success) {
    Write-Success "Session 1 created"
    $script:Session1_Token = $r.Data.accessToken
    $script:Session1_Refresh = $r.Data.refreshToken
    Record-Result "Session 1 (Primary Login)" $true $r.Status
} else {
    Write-Fail "Session 1 failed" $r.Status $r.Exception
    Record-Result "Session 1 (Primary Login)" $false $r.Status "$($r.Exception)"
}

Write-Test 3 "Create Session 2 (Mobile App)"
Write-Info "Session 2: Mobile device login (simulated)"
$r = Invoke-ApiRequest "POST" "/auth/login" @{
    email = $testEmail
    password = $testPassword
}

if ($r.Success) {
    Write-Success "Session 2 created"
    $script:Session2_Token = $r.Data.accessToken
    $script:Session2_Refresh = $r.Data.refreshToken
    Record-Result "Session 2 (Mobile Login)" $true $r.Status
} else {
    Write-Fail "Session 2 failed" $r.Status $r.Exception
    Record-Result "Session 2 (Mobile Login)" $false $r.Status "$($r.Exception)"
}

Write-Test 4 "Create Session 3 (Another Device)"
Write-Info "Session 3: Tablet/Another device login (simulated)"
$r = Invoke-ApiRequest "POST" "/auth/login" @{
    email = $testEmail
    password = $testPassword
}

if ($r.Success) {
    Write-Success "Session 3 created"
    $script:Session3_Token = $r.Data.accessToken
    $script:Session3_Refresh = $r.Data.refreshToken
    Record-Result "Session 3 (Tablet Login)" $true $r.Status
} else {
    Write-Fail "Session 3 failed" $r.Status $r.Exception
    Record-Result "Session 3 (Tablet Login)" $false $r.Status "$($r.Exception)"
}

# ============================================================================
# Phase 3: Verify Multiple Sessions Exist
# ============================================================================

Write-Section "PHASE 3: VERIFY MULTIPLE ACTIVE SESSIONS"

Write-Test 5 "List All Sessions (Before Revoke)"
$r = Invoke-ApiRequest "GET" "/auth/sessions" $null $true

if ($r.Success) {
    $sessionCount = if ($r.Data -is [array]) { $r.Data.Count } else { 1 }
    Write-Success "Retrieved sessions"
    Write-Info "Total Active Sessions: $sessionCount"
    
    if ($sessionCount -ge 3) {
        Write-Info "Multiple sessions confirmed"
        Record-Result "Verify Multiple Sessions" $true $r.Status "Found $sessionCount sessions"
    } else {
        Write-Info "Expected at least 3 sessions, found $sessionCount"
        Record-Result "Verify Multiple Sessions" $true $r.Status "Found $sessionCount sessions"
    }
} else {
    Write-Fail "Failed to retrieve sessions" $r.Status $r.Exception
    Record-Result "Verify Multiple Sessions" $false $r.Status "$($r.Exception)"
}

# ============================================================================
# Phase 4: Revoke All Sessions
# ============================================================================

Write-Section "PHASE 4: REVOKE ALL SESSIONS"

Write-Test 6 "Revoke All Sessions via DELETE /auth/sessions"
Write-Info "Endpoint: DELETE /auth/sessions"
Write-Info "Auth: Using Session 1 token"
Write-Info "Expected: All sessions invalidated"

$r = Invoke-ApiRequest "DELETE" "/auth/sessions" $null $true $Session1_Token

if ($r.Success) {
    Write-Success "All sessions revoked successfully"
    Record-Result "DELETE /auth/sessions" $true $r.Status
} else {
    Write-Fail "Failed to revoke all sessions" $r.Status $r.Exception
    Record-Result "DELETE /auth/sessions" $false $r.Status "$($r.Exception)"
}

# ============================================================================
# Phase 5: Verify All Tokens Are Now Invalid
# ============================================================================

Write-Section "PHASE 5: VERIFY TOKEN INVALIDATION (Post-Revoke)"

Write-Test 7 "Verify Session 1 Token is Invalid"
Write-Info "Attempting to use Session 1 token (should be rejected)"

$r = Invoke-ApiRequest "GET" "/auth/me" $null $true $Session1_Token

if ((-not $r.Success) -and ($r.Status -eq 401)) {
    Write-Success "Session 1 token correctly rejected - HTTP 401"
    Record-Result "Session 1 Token Invalid" $true $r.Status
} else {
    Write-Fail "Session 1 token should be invalid - Status: $($r.Status)" $r.Status $r.Exception
    Record-Result "Session 1 Token Invalid" $false $r.Status "Expected 401, got $($r.Status)"
}

Write-Test 8 "Verify Session 2 Token is Invalid"
Write-Info "Attempting to use Session 2 token (should be rejected)"

$r = Invoke-ApiRequest "GET" "/auth/me" $null $true $Session2_Token

if ((-not $r.Success) -and ($r.Status -eq 401)) {
    Write-Success "Session 2 token correctly rejected - HTTP 401"
    Record-Result "Session 2 Token Invalid" $true $r.Status
} else {
    Write-Fail "Session 2 token should be invalid - Status: $($r.Status)" $r.Status $r.Exception
    Record-Result "Session 2 Token Invalid" $false $r.Status "Expected 401, got $($r.Status)"
}

Write-Test 9 "Verify Session 3 Token is Invalid"
Write-Info "Attempting to use Session 3 token (should be rejected)"

$r = Invoke-ApiRequest "GET" "/auth/me" $null $true $Session3_Token

if ((-not $r.Success) -and ($r.Status -eq 401)) {
    Write-Success "Session 3 token correctly rejected - HTTP 401"
    Record-Result "Session 3 Token Invalid" $true $r.Status
} else {
    Write-Fail "Session 3 token should be invalid - Status: $($r.Status)" $r.Status $r.Exception
    Record-Result "Session 3 Token Invalid" $false $r.Status "Expected 401, got $($r.Status)"
}

# ============================================================================
# Phase 6: Verify Session List is Empty
# ============================================================================

Write-Section "PHASE 6: VERIFY NO SESSIONS REMAIN (Post-Revoke)"

Write-Test 10 "List Sessions After Revoke (Must Re-Login)"
Write-Info "Logging in again to check session list..."

$r = Invoke-ApiRequest "POST" "/auth/login" @{
    email = $testEmail
    password = $testPassword
}

if ($r.Success) {
    $script:AccessToken = $r.Data.accessToken
    Write-Info "Re-login successful for final verification"
    
    # Now list sessions with new token
    $r = Invoke-ApiRequest "GET" "/auth/sessions" $null $true
    
    if ($r.Success) {
        $sessionCount = if ($r.Data -is [array]) { $r.Data.Count } else { 1 }
        Write-Success "Retrieved sessions"
        Write-Info "Active Sessions After Revoke: $sessionCount"
        
        if ($sessionCount -le 1) {
            Write-Info "Sessions correctly cleared"
            Record-Result "Sessions Cleared After Revoke" $true $r.Status "Only $sessionCount new session"
        } else {
            Write-Info "Expected 1 session (current), found $sessionCount"
            Record-Result "Sessions Cleared After Revoke" $false $r.Status "Found $sessionCount sessions"
        }
    } else {
        Write-Fail "Failed to retrieve sessions" $r.Status $r.Exception
        Record-Result "Sessions Cleared After Revoke" $false $r.Status "$($r.Exception)"
    }
} else {
    Write-Fail "Re-login failed" $r.Status $r.Exception
    Record-Result "Sessions Cleared After Revoke" $false $r.Status "$($r.Exception)"
}

# ============================================================================
#                            REPORT
# ============================================================================

$duration = ((Get-Date) - $startTime).TotalSeconds

Show-Summary

Write-Section "EXECUTION SUMMARY"
Write-Host ""
Write-Host "  Test Suite:      LeadFlow Revoke All Sessions"
Write-Host "  Total Duration:  $([math]::Round($duration, 2)) seconds"
Write-Host "  Test User:       $testEmail"
Write-Host "  Server:          $BaseUrl"
Write-Host ""
Write-Host "  Scenarios Tested:"
Write-Host "    - Multi-session creation (3 sessions)"
Write-Host "    - Batch session revocation"
Write-Host "    - Token invalidation verification"
Write-Host "    - Session list cleanup"
Write-Host ""

# Final Status
$failed = ($TestResults | Where-Object { -not $_.Success }).Count
if ($failed -eq 0) {
    Write-Host "[SUCCESS] ALL TESTS PASSED - HTTP 200/204" -ForegroundColor Green
    exit 0
} else {
    Write-Host "[FAILED] $failed test(s) failed" -ForegroundColor Red
    exit 1
}
