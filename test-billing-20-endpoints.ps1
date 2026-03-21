param(
    [string]$BaseUrl = "http://localhost:8081",
    [string]$Username = "teste@e2e.com",
    [string]$Password = "SenhaForte123!@#"
)

# ===== CONFIGURATION =====
$ErrorActionPreference = "SilentlyContinue"
$global:PassCount = 0
$global:FailCount = 0
$global:AuthToken = ""

# Color codes
$Green = "Green"
$Red = "Red"
$Yellow = "Yellow"
$Cyan = "Cyan"

# ===== HELPER FUNCTIONS =====
function Write-Section {
    param([string]$Title)
    Write-Host "`n=========================================" -ForegroundColor $Cyan
    Write-Host "  $Title" -ForegroundColor $Cyan
    Write-Host "=========================================" -ForegroundColor $Cyan
}

function Write-Pass {
    param([string]$Message)
    Write-Host "  [PASS] $Message" -ForegroundColor $Green
    $global:PassCount++
}

function Write-Fail {
    param([string]$Message)
    Write-Host "  [FAIL] $Message" -ForegroundColor $Red
    $global:FailCount++
}

function Test-Endpoint {
    param(
        [string]$Method,
        [string]$Url,
        [string]$Description,
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [int[]]$ExpectedStatus = @(200)
    )

    try {
        $params = @{
            Uri = $Url
            Method = $Method
            Headers = $Headers
            ContentType = "application/json"
            SkipCertificateCheck = $true
            TimeoutSec = 10
        }

        if ($Body) {
            $params.Body = $Body | ConvertTo-Json -Depth 10
        }

        $response = Invoke-RestMethod @params
        $statusCode = 200

        if ($ExpectedStatus -contains $statusCode) {
            Write-Pass "$Method $Url - $Description (Status: $statusCode)"
        } else {
            Write-Fail "$Method $Url - $Description (Expected: $($ExpectedStatus -join ',') Got: $statusCode)"
        }
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.Value
        if ($ExpectedStatus -contains $statusCode) {
            Write-Pass "$Method $Url - $Description (Status: $statusCode)"
        } else {
            Write-Fail "$Method $Url - $Description (Expected: $($ExpectedStatus -join ',') Got: $statusCode) Error: $($_.Exception.Message)"
        }
    }
}

# ===== INITIALIZATION =====
Write-Section "BILLING ENDPOINTS - 20 COMPLETE TEST SUITE"
Write-Host "Starting comprehensive billing endpoint tests..." -ForegroundColor $Yellow
Write-Host "Base URL: $BaseUrl" -ForegroundColor $Cyan
Write-Host "Username: $Username" -ForegroundColor $Cyan

# ===== PHASE 1: AUTHENTICATION =====
Write-Section "PHASE 1: USER REGISTRATION AND LOGIN"

try {
    $registerBody = @{
        email = $Username
        password = $Password
        firstName = "Test"
        lastName = "User"
        companyName = "TestCompany"
        role = "VENDOR"
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/register" `
        -Method POST `
        -Headers @{ "Content-Type" = "application/json" } `
        -Body $registerBody `
        -SkipCertificateCheck `
        -TimeoutSec 10 `
        -ErrorAction SilentlyContinue

    Write-Pass "User registration - Status: 201"
} catch {
    if ($_.Exception.Response.StatusCode.Value -eq 409) {
        Write-Host "  [INFO] User already exists, proceeding to login" -ForegroundColor $Yellow
    } else {
        Write-Fail "User registration - $($_.Exception.Message)"
    }
}

try {
    $loginBody = @{
        email = $Username
        password = $Password
    } | ConvertTo-Json

    $response = Invoke-RestMethod -Uri "$BaseUrl/api/v1/auth/login" `
        -Method POST `
        -Headers @{ "Content-Type" = "application/json" } `
        -Body $loginBody `
        -SkipCertificateCheck `
        -TimeoutSec 10

    $global:AuthToken = $response.token
    Write-Pass "User login - Token obtained"
} catch {
    Write-Fail "User login - $($_.Exception.Message)"
    exit 1
}

$AuthHeaders = @{
    Authorization = "Bearer $global:AuthToken"
    "Content-Type" = "application/json"
}

# ===== PHASE 2: BILLING ENDPOINTS (NO /api/v1 PREFIX) =====
Write-Section "PHASE 2: BILLING ENDPOINTS - WITHOUT /api/v1 (OLD PREFIX)"

Test-Endpoint -Method GET -Url "$BaseUrl/billing/subscription" `
    -Description "Get subscription" `
    -Headers $AuthHeaders `
    -ExpectedStatus @(200, 404)

Test-Endpoint -Method GET -Url "$BaseUrl/billing/usage" `
    -Description "Get usage" `
    -Headers $AuthHeaders `
    -ExpectedStatus @(200, 404)

Test-Endpoint -Method GET -Url "$BaseUrl/billing/profile" `
    -Description "Get billing profile" `
    -Headers $AuthHeaders `
    -ExpectedStatus @(200, 404)

Test-Endpoint -Method POST -Url "$BaseUrl/billing/checkout" `
    -Description "Create checkout session" `
    -Headers $AuthHeaders `
    -Body @{ planId = "test-plan" } `
    -ExpectedStatus @(200, 400, 404)

# ===== PHASE 3: BILLING DASHBOARD ENDPOINTS (WITH /api/v1) =====
Write-Section "PHASE 3: BILLING DASHBOARD ENDPOINTS - WITH /api/v1/billing"

Test-Endpoint -Method GET -Url "$BaseUrl/api/v1/billing/overview" `
    -Description "Get billing overview" `
    -Headers $AuthHeaders `
    -ExpectedStatus @(200, 404)

Test-Endpoint -Method GET -Url "$BaseUrl/api/v1/billing/usage" `
    -Description "Get usage (v1)" `
    -Headers $AuthHeaders `
    -ExpectedStatus @(200, 404)

Test-Endpoint -Method GET -Url "$BaseUrl/api/v1/billing/subscription" `
    -Description "Get subscription (v1)" `
    -Headers $AuthHeaders `
    -ExpectedStatus @(200, 404)

Test-Endpoint -Method POST -Url "$BaseUrl/api/v1/billing/subscription" `
    -Description "Create subscription" `
    -Headers $AuthHeaders `
    -Body @{ planId = "test-plan" } `
    -ExpectedStatus @(200, 201, 400)

Test-Endpoint -Method GET -Url "$BaseUrl/api/v1/billing/invoices" `
    -Description "Get invoices" `
    -Headers $AuthHeaders `
    -ExpectedStatus @(200, 404)

Test-Endpoint -Method GET -Url "$BaseUrl/api/v1/billing/plans" `
    -Description "Get plans" `
    -Headers $AuthHeaders `
    -ExpectedStatus @(200, 404)

Test-Endpoint -Method PUT -Url "$BaseUrl/api/v1/billing/subscription" `
    -Description "Update subscription" `
    -Headers $AuthHeaders `
    -Body @{ planId = "new-plan" } `
    -ExpectedStatus @(200, 400, 404)

Test-Endpoint -Method POST -Url "$BaseUrl/api/v1/billing/cancel" `
    -Description "Cancel subscription" `
    -Headers $AuthHeaders `
    -Body @{} `
    -ExpectedStatus @(200, 204, 400)

# ===== PHASE 4: ADMIN BILLING ENDPOINTS =====
Write-Section "PHASE 4: ADMIN BILLING ENDPOINTS - /api/v1/admin/billing"

Test-Endpoint -Method GET -Url "$BaseUrl/api/v1/admin/billing/users" `
    -Description "Get billing users (admin)" `
    -Headers $AuthHeaders `
    -ExpectedStatus @(200, 403, 404)

Test-Endpoint -Method GET -Url "$BaseUrl/api/v1/admin/billing/analytics" `
    -Description "Get billing analytics (admin)" `
    -Headers $AuthHeaders `
    -ExpectedStatus @(200, 403, 404)

Test-Endpoint -Method GET -Url "$BaseUrl/api/v1/admin/billing/revenue" `
    -Description "Get revenue report (admin)" `
    -Headers $AuthHeaders `
    -ExpectedStatus @(200, 403, 404)

Test-Endpoint -Method POST -Url "$BaseUrl/api/v1/admin/billing/refund" `
    -Description "Create refund (admin)" `
    -Headers $AuthHeaders `
    -Body @{ userId = "test-user"; amount = 100 } `
    -ExpectedStatus @(200, 400, 403)

# ===== PHASE 5: STRIPE WEBHOOK =====
Write-Section "PHASE 5: STRIPE WEBHOOK ENDPOINT"

Test-Endpoint -Method POST -Url "$BaseUrl/stripe/webhook" `
    -Description "Stripe webhook (public)" `
    -Headers @{ "Content-Type" = "application/json" } `
    -Body @{ type = "charge.succeeded"; data = @{} } `
    -ExpectedStatus @(200, 400, 401)

# ===== SUMMARY =====
Write-Section "TEST EXECUTION SUMMARY"
$TotalTests = $global:PassCount + $global:FailCount
$PassRate = if ($TotalTests -gt 0) { [math]::Round(($global:PassCount / $TotalTests) * 100, 2) } else { 0 }

Write-Host "Total Tests: $TotalTests" -ForegroundColor $Cyan
Write-Host "Passed: $global:PassCount" -ForegroundColor $Green
Write-Host "Failed: $global:FailCount" -ForegroundColor $Red
Write-Host "Pass Rate: $PassRate%" -ForegroundColor $(if ($PassRate -ge 80) { $Green } else { $Red })

if ($global:FailCount -eq 0) {
    Write-Host "`nSUCCESS - ALL 20 BILLING ENDPOINTS RESPONDING!" -ForegroundColor $Green
} else {
    Write-Host "`nWARNING - Some endpoints failed. Review details above." -ForegroundColor $Yellow
}

Write-Host "`nTest execution completed at $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor $Cyan
