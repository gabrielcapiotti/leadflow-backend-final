param()

$BaseUrl = "http://localhost:8081"
$TenantHeader = "public"
$ProgressPreference = 'SilentlyContinue'

Write-Host "`n════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "LEADFLOW LEADS ENDPOINTS - INTEGRATION TESTS" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "Server: $BaseUrl"
Write-Host "Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host "════════════════════════════════════════════════════════`n" -ForegroundColor Cyan

# Utility Functions
function Invoke-ApiRequest {
    param(
        [string]$Method,
        [string]$Endpoint,
        [hashtable]$Body = $null,
        [bool]$RequireAuth = $false,
        [string]$Token = $null
    )

    $Headers = @{
        "Content-Type" = "application/json"
        "X-Tenant-Id"  = $TenantHeader
    }

    if ($RequireAuth -and $Token) {
        $Headers["Authorization"] = "Bearer $Token"
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
        
        return @{
            Success = $false
            Status  = $status
            Exception = $_.Exception.Message
        }
    }
}

# Step 1: Register New User
Write-Host "STEP 1: Register New User" -ForegroundColor Yellow
Write-Host "═════════════════════════════════════════════════════════"
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$testEmail = "lead-test-$timestamp@leadflow.dev"
$testPassword = "TestPassword123!@"

$r = Invoke-ApiRequest "POST" "/auth/register" @{
    name = "Lead Tester"
    email = $testEmail
    password = $testPassword
    confirmPassword = $testPassword
}

if ($r.Success) {
    Write-Host "✅ User registered" -ForegroundColor Green
    Write-Host "   Email: $testEmail"
    $token = $r.Data.accessToken
} else {
    Write-Host "❌ Failed to register: $($r.Exception)" -ForegroundColor Red
    exit 1
}
Write-Host ""

# Define headers for authenticated requests
$authHeaders = @{
    "Content-Type" = "application/json"
    "X-Tenant-ID" = $TenantHeader
    "Authorization" = "Bearer $token"
}

# Test Results
$TestCount = 0
$PassCount = 0
$FailCount = 0

# STEP 2: Create Lead
Write-Host "STEP 2: Create Lead (LeadController)" -ForegroundColor Yellow
Write-Host "═════════════════════════════════════════════════════════"
$TestCount++

$leadBody = @{
    name = "John Doe"
    email = "john-$timestamp@example.com"
    phone = "+5511999999999"
}

$r = Invoke-ApiRequest "POST" "/api/leads" $leadBody $true $token

if ($r.Success) {
    Write-Host "✅ Lead created (HTTP $($r.Status))" -ForegroundColor Green
    $leadId = $r.Data.id
    Write-Host "   Lead ID: $leadId"
    $PassCount++
} else {
    Write-Host "❌ Failed to create lead (HTTP $($r.Status))" -ForegroundColor Red
    Write-Host "   Error: $($r.Exception)"
    $FailCount++
    $leadId = $null
}
Write-Host ""

# STEP 3: List Leads
Write-Host "STEP 3: List Leads (LeadController)" -ForegroundColor Yellow
Write-Host "═════════════════════════════════════════════════════════"
$TestCount++

$r = Invoke-ApiRequest "GET" "/api/leads" $null $true $token

if ($r.Success) {
    Write-Host "✅ Listed leads (HTTP $($r.Status))" -ForegroundColor Green
    $leads = $r.Data
    Write-Host "   Total leads: $(if ($leads -is [array]) { $leads.Count } else { 1 })"
    $PassCount++
} else {
    Write-Host "❌ Failed to list leads (HTTP $($r.Status))" -ForegroundColor Red
    Write-Host "   Error: $($r.Exception)"
    $FailCount++
}
Write-Host ""

# STEP 4: Get Lead by ID
Write-Host "STEP 4: Get Lead by ID (LeadController)" -ForegroundColor Yellow
Write-Host "═════════════════════════════════════════════════════════"
$TestCount++

if ($leadId) {
    $r = Invoke-ApiRequest "GET" "/api/leads/$leadId" $null $true $token
    
    if ($r.Success) {
        Write-Host "✅ Retrieved lead (HTTP $($r.Status))" -ForegroundColor Green
        Write-Host "   Name: $($r.Data.name)"
        Write-Host "   Email: $($r.Data.email)"
        $PassCount++
    } else {
        Write-Host "❌ Failed to retrieve lead (HTTP $($r.Status))" -ForegroundColor Red
        Write-Host "   Error: $($r.Exception)"
        $FailCount++
    }
} else {
    Write-Host "⚠️  Skipped (no lead ID from previous test)" -ForegroundColor Gray
}
Write-Host ""

# STEP 5: List Vendor Leads
Write-Host "STEP 5: List Vendor Leads (VendorLeadController)" -ForegroundColor Yellow
Write-Host "═════════════════════════════════════════════════════════"
$TestCount++

$r = Invoke-ApiRequest "GET" "/api/vendor-leads?page=0&size=10" $null $true $token

if ($r.Success) {
    Write-Host "✅ Listed vendor leads (HTTP $($r.Status))" -ForegroundColor Green
    $PassCount++
} else {
    Write-Host "❌ Failed to list vendor leads (HTTP $($r.Status))" -ForegroundColor Red
    Write-Host "   Error: $($r.Exception)"
    $FailCount++
}
Write-Host ""

# STEP 6: Get Vendor Lead Metrics
Write-Host "STEP 6: Get Vendor Lead Metrics" -ForegroundColor Yellow
Write-Host "═════════════════════════════════════════════════════════"
$TestCount++

$r = Invoke-ApiRequest "GET" "/api/vendor-leads/metrics" $null $true $token

if ($r.Success) {
    Write-Host "✅ Retrieved metrics (HTTP $($r.Status))" -ForegroundColor Green
    $PassCount++
} else {
    Write-Host "❌ Failed to get metrics (HTTP $($r.Status))" -ForegroundColor Red
    Write-Host "   Error: $($r.Exception)"
    $FailCount++
}
Write-Host ""

# STEP 7: Get Vendor Lead Ranking
Write-Host "STEP 7: Get Vendor Lead Ranking" -ForegroundColor Yellow
Write-Host "═════════════════════════════════════════════════════════"
$TestCount++

$r = Invoke-ApiRequest "GET" "/api/vendor-leads/ranking" $null $true $token

if ($r.Success) {
    Write-Host "✅ Retrieved ranking (HTTP $($r.Status))" -ForegroundColor Green
    $PassCount++
} else {
    Write-Host "❌ Failed to get ranking (HTTP $($r.Status))" -ForegroundColor Red
    Write-Host "   Error: $($r.Exception)"
    $FailCount++
}
Write-Host ""

# STEP 8: Get Stage Time Metrics
Write-Host "STEP 8: Get Stage Time Metrics" -ForegroundColor Yellow
Write-Host "═════════════════════════════════════════════════════════"
$TestCount++

$r = Invoke-ApiRequest "GET" "/api/vendor-leads/metrics/stage-time" $null $true $token

if ($r.Success) {
    Write-Host "✅ Retrieved stage time metrics (HTTP $($r.Status))" -ForegroundColor Green
    $PassCount++
} else {
    Write-Host "❌ Failed to get stage time metrics (HTTP $($r.Status))" -ForegroundColor Red
    Write-Host "   Error: $($r.Exception)"
    $FailCount++
}
Write-Host ""

# STEP 9: Get Conversion Metrics
Write-Host "STEP 9: Get Conversion Metrics" -ForegroundColor Yellow
Write-Host "═════════════════════════════════════════════════════════"
$TestCount++

$r = Invoke-ApiRequest "GET" "/api/vendor-leads/metrics/conversion" $null $true $token

if ($r.Success) {
    Write-Host "✅ Retrieved conversion metrics (HTTP $($r.Status))" -ForegroundColor Green
    $PassCount++
} else {
    Write-Host "❌ Failed to get conversion metrics (HTTP $($r.Status))" -ForegroundColor Red
    Write-Host "   Error: $($r.Exception)"
    $FailCount++
}
Write-Host ""

# Summary
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "TEST SUMMARY" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "Total Tests:  $TestCount"
Write-Host "Passed:       $PassCount" -ForegroundColor Green
Write-Host "Failed:       $FailCount" $(if ($FailCount -eq 0) { "-ForegroundColor Green" } else { "-ForegroundColor Red" })
Write-Host "Pass Rate:    $([math]::Round(($PassCount/$TestCount)*100, 2))%"
Write-Host "════════════════════════════════════════════════════════`n" -ForegroundColor Cyan
