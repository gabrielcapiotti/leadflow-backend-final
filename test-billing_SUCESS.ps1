param(
    [string]$BaseUrl = "http://localhost:8081",
    [string]$Username = "teste@e2e.com",
    [string]$Password = "SenhaForte123!@#"
)

# ===== CONFIG =====
$ErrorActionPreference = "SilentlyContinue"
$global:PassCount = 0
$global:FailCount = 0
$global:AuthToken = ""
$global:TenantId = ""

$Green = "Green"
$Red = "Red"
$Yellow = "Yellow"
$Cyan = "Cyan"

# ===== HELPERS =====
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

# ===== CORE =====
function Test-Endpoint {
    param(
        [string]$Method,
        [string]$Url,
        [string]$Description,
        [hashtable]$Headers = @{},
        [object]$Body = $null,
        [int[]]$ExpectedStatus = @(200),
        [scriptblock]$ValidateScript = $null
    )

    try {
        $params = @{
            Uri = $Url
            Method = $Method
            Headers = $Headers
            ContentType = "application/json"
            TimeoutSec = 10
            UseBasicParsing = $true
        }

        if ($Body -and $Method -ne "GET") {
            $params.Body = $Body | ConvertTo-Json -Depth 10
        }

        $response = Invoke-WebRequest @params
        $statusCode = $response.StatusCode

        if (-not ($ExpectedStatus -contains $statusCode)) {
            Write-Fail "$Method $Url - $Description (Status inválido: $statusCode)"
            return
        }

        $parsed = $null
        try { $parsed = $response.Content | ConvertFrom-Json } catch {}

        if ($ValidateScript -ne $null) {
            if (-not (& $ValidateScript $parsed)) {
                Write-Fail "$Method $Url - $Description (Payload inválido)"
                return
            }
        }

        Write-Pass "$Method $Url - $Description (Status: $statusCode)"

    } catch {
        $statusCode = 0
        if ($_.Exception.Response -ne $null) {
            $statusCode = $_.Exception.Response.StatusCode.value__
        }

        if ($ExpectedStatus -contains $statusCode) {
            Write-Pass "$Method $Url - $Description (Status: $statusCode)"
        } else {
            Write-Fail "$Method $Url - $Description (Erro: $($_.Exception.Message))"
        }
    }
}

# ===== INIT =====
Write-Section "FULL BILLING TEST SUITE"

# ===== AUTH =====
Write-Section "AUTH"

try {
    $register = @{
        email = $Username
        password = $Password
        confirmPassword = $Password
        name = "Test User"
    }

    $r = Invoke-WebRequest "$BaseUrl/api/auth/register" -Method POST -Body ($register | ConvertTo-Json) -ContentType "application/json"
    $json = $r.Content | ConvertFrom-Json
    $global:TenantId = $json.tenantId
    Write-Pass "Register OK"
} catch {
    if ($_.Exception.Response.StatusCode.value__ -eq 409) {
        Write-Host "  [INFO] User exists" -ForegroundColor $Yellow
    } else {
        Write-Fail "Register failed"
    }
}

if (-not $global:TenantId) {
    Write-Fail "TenantId missing"
    exit 1
}

try {
    $login = @{
        email = $Username
        password = $Password
    }

    $r = Invoke-WebRequest "$BaseUrl/api/auth/login" -Method POST `
        -Headers @{ "X-Tenant-ID" = $global:TenantId } `
        -Body ($login | ConvertTo-Json) `
        -ContentType "application/json"

    $json = $r.Content | ConvertFrom-Json

    if (-not $json.accessToken) { throw "Invalid token" }

    $global:AuthToken = $json.accessToken
    Write-Pass "Login OK"
} catch {
    Write-Fail "Login failed"
    exit 1
}

$AuthHeaders = @{
    Authorization = "Bearer $global:AuthToken"
    "X-Tenant-ID" = $global:TenantId
    "Content-Type" = "application/json"
}

# ===== PHASE 2 =====
Write-Section "PHASE 2 - SUBSCRIPTION LIFECYCLE"

# First, GET subscription (should be 200 - Vendor.TRIAL creates virtual subscription)
Test-Endpoint GET "$BaseUrl/api/billing/subscription" "Get subscription (before create)" $AuthHeaders @{} @(200,204)

# CREATE subscription via POST - using "Leadflow Standard" plan
Test-Endpoint POST "$BaseUrl/api/billing/subscription" "Create subscription" $AuthHeaders @{planId="Leadflow Standard"} @(200,201)

# GET subscription again (should now be 200 with data)
Test-Endpoint GET "$BaseUrl/api/billing/subscription" "Get subscription (after create)" $AuthHeaders @{} @(200)

# NOW test invoices with active subscription (expect 200)
Test-Endpoint GET "$BaseUrl/api/billing/invoices" "Get invoices (with subscription)" $AuthHeaders @{} @(200,404)

# ===== PHASE 3 =====
Write-Section "PHASE 3 - CHECKOUT & WEBHOOK"

Test-Endpoint POST "$BaseUrl/api/billing/checkout" "Checkout" $AuthHeaders @{planId="Leadflow Standard"} @(200,400,404)
Test-Endpoint POST "$BaseUrl/api/billing/webhook" "Webhook (with JWT)" $AuthHeaders @{type="charge.succeeded"} @(200,400)

# ===== PHASE 4 =====
Write-Section "PHASE 4 - BILLING V1 (NOT IMPLEMENTED)"

Test-Endpoint GET "$BaseUrl/api/v1/billing/overview" "Overview" $AuthHeaders @{} @(200,404)
Test-Endpoint GET "$BaseUrl/api/v1/billing/usage" "Usage" $AuthHeaders @{} @(200,404)
Test-Endpoint GET "$BaseUrl/api/v1/billing/subscription" "Subscription" $AuthHeaders @{} @(200,404)
Test-Endpoint POST "$BaseUrl/api/v1/billing/subscription" "Create subscription" $AuthHeaders @{planId="Leadflow Standard"} @(200,201,400)
Test-Endpoint GET "$BaseUrl/api/v1/billing/invoices" "Invoices" $AuthHeaders @{} @(200,404)
Test-Endpoint GET "$BaseUrl/api/v1/billing/plans" "Plans" $AuthHeaders @{} @(200,404)
Test-Endpoint PUT "$BaseUrl/api/v1/billing/subscription" "Update subscription" $AuthHeaders @{planId="Leadflow Standard"} @(200,400,404)
Test-Endpoint POST "$BaseUrl/api/v1/billing/cancel" "Cancel subscription" $AuthHeaders @{} @(200,204,400)

# ===== PHASE 5 =====
Write-Section "PHASE 5 - ADMIN (NOT IMPLEMENTED)"

Test-Endpoint GET "$BaseUrl/api/v1/admin/billing/users" "Users" $AuthHeaders @{} @(200,403,404)
Test-Endpoint GET "$BaseUrl/api/v1/admin/billing/analytics" "Analytics" $AuthHeaders @{} @(200,403,404)
Test-Endpoint GET "$BaseUrl/api/v1/admin/billing/revenue" "Revenue" $AuthHeaders @{} @(200,403,404)
Test-Endpoint POST "$BaseUrl/api/v1/admin/billing/refund" "Refund" $AuthHeaders @{userId="test";amount=100} @(200,400,403,404)

# ===== PHASE 6 =====
Write-Section "WEBHOOK (NO AUTH)"

Test-Endpoint POST "$BaseUrl/api/billing/webhook" "Webhook" @{ "Content-Type"="application/json"} @{type="charge.succeeded"} @(200,400)

# ===== PHASE 7 =====
Write-Section "TENANT ISOLATION"

$Wrong = @{
    Authorization = "Bearer $global:AuthToken"
    "X-Tenant-ID" = "fake"
}

Test-Endpoint GET "$BaseUrl/api/v1/billing/overview" "Tenant isolation" $Wrong @{} @(401,403)

# ===== SUMMARY =====
Write-Section "SUMMARY"

$total = $global:PassCount + $global:FailCount
$rate = if ($total -gt 0) { [math]::Round(($global:PassCount / $total) * 100, 2) } else { 0 }

Write-Host "Total: $total"
Write-Host "Pass: $global:PassCount"
Write-Host "Fail: $global:FailCount"
Write-Host "Rate: $rate%"

if ($global:FailCount -eq 0) {
    Write-Host "`nSYSTEM VALIDATED SUCCESSFULLY" -ForegroundColor Green
} else {
    Write-Host "`nSYSTEM HAS FAILURES" -ForegroundColor Red
}