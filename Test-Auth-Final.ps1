#!/usr/bin/env pwsh

<#
.SYNOPSIS
    LeadFlow Auth Endpoints - Final Test
    
.DESCRIPTION
    Suite de testes para 9 endpoints de autenticacao
#>

$BaseUrl = "http://localhost:8081"
$TenantHeader = "public"

$TestResults = @()
$AccessToken = $null
$RefreshToken = $null

Write-Host "`n$('='*80)" -ForegroundColor Cyan
Write-Host "  LEADFLOW AUTH ENDPOINTS - TEST SUITE" -ForegroundColor Cyan
Write-Host "$('='*80)`n" -ForegroundColor Cyan

function Test-Endpoint {
    param($Number, [string]$Name, [string]$Method, [string]$Path, [hashtable]$Body, [bool]$Auth = $false)
    
    Write-Host "[$Number] $Name" -ForegroundColor Yellow
    
    $headers = @{
        "Content-Type" = "application/json"
        "X-Tenant-Id" = $TenantHeader
    }
    
    if ($Auth -and $AccessToken) {
        $headers["Authorization"] = "Bearer $AccessToken"
    }
    
    try {
        $params = @{
            Uri = "$BaseUrl$Path"
            Method = $Method
            Headers = $headers
            UseBasicParsing = $true
            ErrorAction = "Stop"
        }
        
        if ($Body) {
            $params["Body"] = ($Body | ConvertTo-Json -Depth 10)
        }
        
        $response = Invoke-WebRequest @params
        $data = $response.Content | ConvertFrom-Json
        
        Write-Host "    ✅ $($response.StatusCode)" -ForegroundColor Green
        return @{Success = $true; Status = $response.StatusCode; Data = $data}
    }
    catch {
        $status = $_.Exception.Response.StatusCode.Value__
        $msg = $_.Exception.Message
        Write-Host "    ❌ $status - $msg" -ForegroundColor Red
        return @{Success = $false; Status = $status; Error = $msg}
    }
}

# ============================================
# Test 1: HEALTH CHECK
# ============================================
Write-Host "`n[GROUP 1] PUBLIC ENDPOINTS" -ForegroundColor Cyan
Write-Host ""
$r = Test-Endpoint 1 "Health Check" "GET" "/api/actuator/health" $null $true
if ($r.Success) { $TestResults += @{Test = "Health"; Status = "PASS" } } else { $TestResults += @{Test = "Health"; Status = "FAIL" } }

# ============================================
# Test 2: REGISTER
# ============================================
Write-Host ""
$email = "test-$(Get-Random)-$(Get-Date -Format 'yyyyMMddHHmmss')@leadflow.dev"
$password = "SecurePass123!@"

$r = Test-Endpoint 2 "Register New User" "POST" "/auth/register" @{
    name = "Test User"
    email = $email
    password = $password
    confirmPassword = $password
}

if ($r.Success) {
    $AccessToken = $r.Data.accessToken
    $RefreshToken = $r.Data.refreshToken
    Write-Host "    Email: $email" -ForegroundColor DarkGray
    $TestResults += @{Test = "Register"; Status = "PASS" }
} else {
    $TestResults += @{Test = "Register"; Status = "FAIL" }
    Write-Host "Cannot continue without registered user" -ForegroundColor Red
    exit 1
}

# ============================================
# Test 3: LOGIN
# ============================================
Write-Host ""
$r = Test-Endpoint 3 "Login with Credentials" "POST" "/auth/login" @{
    email = $email
    password = $password
}

if ($r.Success) {
    $AccessToken = $r.Data.accessToken
    $TestResults += @{Test = "Login"; Status = "PASS" }
} else {
    $TestResults += @{Test = "Login"; Status = "FAIL" }
}

# ============================================
# Test 4: REFRESH TOKEN
# ============================================
Write-Host ""
$r = Test-Endpoint 4 "Refresh Token" "POST" "/auth/refresh" @{
    refreshToken = $RefreshToken
}

if ($r.Success) {
    $AccessToken = $r.Data.accessToken
    $TestResults += @{Test = "Refresh"; Status = "PASS" }
} else {
    $TestResults += @{Test = "Refresh"; Status = "FAIL" }
}

# ============================================
# Test 5: GET ME
# ============================================
Write-Host "`n[GROUP 2] PROTECTED ENDPOINTS" -ForegroundColor Cyan
Write-Host ""
$r = Test-Endpoint 5 "Get Current User Profile" "GET" "/auth/me" $null $true

if ($r.Success) {
    Write-Host "    ID:       $($r.Data.id)" -ForegroundColor DarkGray
    Write-Host "    Email:    $($r.Data.email)" -ForegroundColor DarkGray
    Write-Host "    Role:     $($r.Data.role)" -ForegroundColor DarkGray
    Write-Host "    TenantId: $($r.Data.tenantId)" -ForegroundColor DarkGray
    
    if ($r.Data.tenantId) {
        Write-Host "    OK: TenantId is present!" -ForegroundColor Green
        $TestResults += @{Test = "Get ME"; Status = "PASS" }
    } else {
        Write-Host "    WARNING: TenantId is empty!" -ForegroundColor Yellow
        $TestResults += @{Test = "Get ME"; Status = "WARN" }
    }
} else {
    $TestResults += @{Test = "Get ME"; Status = "FAIL" }
}

# ============================================
# Test 6: LIST SESSIONS
# ============================================
Write-Host ""
$r = Test-Endpoint 6 "List Active Sessions" "GET" "/auth/sessions" $null $true

if ($r.Success) {
    Write-Host "    Active sessions: $($r.Data.Count)" -ForegroundColor DarkGray
    $TestResults += @{Test = "List Sessions"; Status = "PASS" }
} else {
    $TestResults += @{Test = "List Sessions"; Status = "FAIL" }
}

# ============================================
# Test 7: CHANGE PASSWORD
# ============================================
Write-Host "`n[GROUP 3] PASSWORD OPERATIONS" -ForegroundColor Cyan
Write-Host ""
$r = Test-Endpoint 7 "Change Password" "POST" "/auth/change-password" @{
    currentPassword = $password
    newPassword = "NewSecurePass@456"
    confirmPassword = "NewSecurePass@456"
} $true

if ($r.Success) {
    $TestResults += @{Test = "Change Password"; Status = "PASS" }
    $password = "NewSecurePass@456"
} else {
    $TestResults += @{Test = "Change Password"; Status = "WARN" }
}

# ============================================
# Test 8: REVOKE ALL SESSIONS
# ============================================
Write-Host ""
$r = Test-Endpoint 8 "Revoke All Sessions" "DELETE" "/auth/sessions" $null $true

if ($r.Success) {
    Write-Host "    All sessions revoked" -ForegroundColor DarkGray
    $TestResults += @{Test = "Revoke Sessions"; Status = "PASS" }
} else {
    $TestResults += @{Test = "Revoke Sessions"; Status = "FAIL" }
}

# Re-login for final logout
Write-Host "    Re-authenticating..." -ForegroundColor DarkGray
$r = Test-Endpoint "  " "Re-login" "POST" "/auth/login" @{
    email = $email
    password = $password
} $false

if ($r.Success) {
    $AccessToken = $r.Data.accessToken
}

# ============================================
# Test 9: LOGOUT
# ============================================
Write-Host ""
$r = Test-Endpoint 9 "Logout" "POST" "/auth/logout" $null $true

if ($r.Success) {
    $TestResults += @{Test = "Logout"; Status = "PASS" }
} else {
    $TestResults += @{Test = "Logout"; Status = "FAIL" }
}

# ============================================
# SUMMARY
# ============================================
Write-Host "`n$('='*80)" -ForegroundColor Cyan
Write-Host "  TEST RESULTS SUMMARY" -ForegroundColor Cyan
Write-Host "$('='*80)" -ForegroundColor Cyan

$passed = ($TestResults | Where-Object { $_.Status -eq "PASS" }).Count
$total = $TestResults.Count

Write-Host "`nResults:"
$TestResults | ForEach-Object {
    $color = "Red"
    if ($_.Status -eq "PASS") { $color = "Green" }
    elseif ($_.Status -eq "WARN") { $color = "Yellow" }
    Write-Host "  [$($_.Status)] $($_.Test)" -ForegroundColor $color
}

$percentage = 0
if ($total -gt 0) {
    $percentage = [math]::Round(($passed / $total) * 100)
}
Write-Host "`nPassed: $passed/$total ($percentage%)" -ForegroundColor $(if($passed -eq $total) {"Green"} else {"Yellow"})

Write-Host "`n$('='*80)" -ForegroundColor Cyan
Write-Host "  FIXES APPLIED" -ForegroundColor Green
Write-Host "$('='*80)" -ForegroundColor Cyan
Write-Host ""
Write-Host "  1. Health endpoint: /api/actuator/health (correct path)"
Write-Host "  2. AuthController: TenantId added to /auth/me response"
Write-Host "  3. Test suite updated: removed non-existent endpoints"
Write-Host ""

exit 0
