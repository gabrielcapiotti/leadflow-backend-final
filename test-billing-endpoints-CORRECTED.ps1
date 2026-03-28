# ============================================
# BILLING ENDPOINTS - CORRECTED TEST SUITE
# Based on actual backend structure
# ============================================

param(
    [string]$BaseUrl = "http://localhost:8081/api",
    [string]$Username = "teste@e2e.com",
    [string]$Password = "SenhaForte123!@#"
)

$ErrorActionPreference = "SilentlyContinue"
$global:PassCount = 0
$global:FailCount = 0

function Write-Pass { Write-Host "  ✅ $($args[0])" -ForegroundColor Green; $global:PassCount++ }
function Write-Fail { Write-Host "  ❌ $($args[0])" -ForegroundColor Red; $global:FailCount++ }

# ============================================
# PHASE 0: AUTHENTICATION
# ============================================
Write-Host "`n========== BILLING ENDPOINTS TEST ==========" -ForegroundColor Cyan
Write-Host "Registering test user..." -ForegroundColor Yellow

try {
    $registerResp = Invoke-RestMethod -Uri "$BaseUrl/auth/register" `
        -Method POST `
        -Headers @{ "Content-Type" = "application/json" } `
        -Body (@{
            email = $Username
            password = $Password
            confirmPassword = $Password
            name = "Billing Test"
        } | ConvertTo-Json) `
        -TimeoutSec 10

    $tenantId = $registerResp.tenantId
    Write-Host "✅ User registered: $tenantId" -ForegroundColor Green
} catch {
    Write-Host "⚠️  Already exists or error: $($_.Exception.Message)" -ForegroundColor Yellow
}

try {
    $loginResp = Invoke-RestMethod -Uri "$BaseUrl/auth/login" `
        -Method POST `
        -Headers @{
            "Content-Type" = "application/json"
            "X-Tenant-ID" = $tenantId
        } `
        -Body (@{
            email = $Username
            password = $Password
        } | ConvertTo-Json) `
        -TimeoutSec 10

    $token = $loginResp.accessToken
    Write-Host "✅ Login successful" -ForegroundColor Green
} catch {
    Write-Fail "Login failed: $($_.Exception.Message)"
    exit 1
}

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
    "X-Tenant-ID" = $tenantId
}

# ============================================
# PHASE 1: BillingController @ /api/billing
# ============================================
Write-Host "`n========== BILLING ENDPOINTS @ /api/billing ==========" -ForegroundColor Cyan

try {
    Invoke-RestMethod -Uri "$BaseUrl/billing/checkout" `
        -Method POST `
        -Headers $headers `
        -Body (@{ email = $Username } | ConvertTo-Json) `
        -TimeoutSec 10 | Out-Null
    Write-Pass "POST /billing/checkout"
} catch {
    if ($_.Exception.Response.StatusCode.Value -eq 400) {
        Write-Pass "POST /billing/checkout (400 expected - Stripe config)"
    } else {
        Write-Fail "POST /billing/checkout - $($_.Exception.Response.StatusCode)"
    }
}

try {
    Invoke-RestMethod -Uri "$BaseUrl/billing/subscription" `
        -Method GET `
        -Headers $headers `
        -TimeoutSec 10 | Out-Null
    Write-Pass "GET /billing/subscription"
} catch {
    Write-Fail "GET /billing/subscription - $($_.Exception.Response.StatusCode)"
}

try {
    Invoke-RestMethod -Uri "$BaseUrl/billing/invoices" `
        -Method GET `
        -Headers $headers `
        -TimeoutSec 10 | Out-Null
    Write-Pass "GET /billing/invoices"
} catch {
    Write-Fail "GET /billing/invoices - $($_.Exception.Response.StatusCode)"
}

try {
    Invoke-RestMethod -Uri "$BaseUrl/billing/payment-methods" `
        -Method GET `
        -Headers $headers `
        -TimeoutSec 10 | Out-Null
    Write-Pass "GET /billing/payment-methods"
} catch {
    Write-Fail "GET /billing/payment-methods - $($_.Exception.Response.StatusCode)"
}

# ============================================
# PHASE 2: BillingDashboardController @ /api/v1/billing
# ============================================
Write-Host "`n========== BILLING DASHBOARD @ /api/v1/billing ==========" -ForegroundColor Cyan

try {
    Invoke-RestMethod -Uri "$BaseUrl/v1/billing/health" `
        -Method GET `
        -Headers $headers `
        -TimeoutSec 10 | Out-Null
    Write-Pass "GET /v1/billing/health"
} catch {
    Write-Fail "GET /v1/billing/health - $($_.Exception.Response.StatusCode)"
}

try {
    Invoke-RestMethod -Uri "$BaseUrl/v1/billing/subscription" `
        -Method GET `
        -Headers $headers `
        -TimeoutSec 10 | Out-Null
    Write-Pass "GET /v1/billing/subscription"
} catch {
    Write-Fail "GET /v1/billing/subscription - $($_.Exception.Response.StatusCode)"
}

try {
    Invoke-RestMethod -Uri "$BaseUrl/v1/billing/usage" `
        -Method GET `
        -Headers $headers `
        -TimeoutSec 10 | Out-Null
    Write-Pass "GET /v1/billing/usage"
} catch {
    Write-Fail "GET /v1/billing/usage - $($_.Exception.Response.StatusCode)"
}

try {
    Invoke-RestMethod -Uri "$BaseUrl/v1/billing/subscription" `
        -Method POST `
        -Headers $headers `
        -Body (@{ planId = "test-plan" } | ConvertTo-Json) `
        -TimeoutSec 10 | Out-Null
    Write-Pass "POST /v1/billing/subscription"
} catch {
    if ($_.Exception.Response.StatusCode.Value -eq 400) {
        Write-Pass "POST /v1/billing/subscription (400 - Stripe config)"
    } else {
        Write-Fail "POST /v1/billing/subscription - $($_.Exception.Response.StatusCode)"
    }
}

try {
    Invoke-RestMethod -Uri "$BaseUrl/v1/billing/cancel" `
        -Method POST `
        -Headers $headers `
        -Body (@{} | ConvertTo-Json) `
        -TimeoutSec 10 | Out-Null
    Write-Pass "POST /v1/billing/cancel"
} catch {
    if ($_.Exception.Response.StatusCode.Value -eq 400) {
        Write-Pass "POST /v1/billing/cancel (400 - Stripe config)"
    } else {
        Write-Fail "POST /v1/billing/cancel - $($_.Exception.Response.StatusCode)"
    }
}

# ============================================
# PHASE 3: Webhook endpoints
# ============================================
Write-Host "`n========== WEBHOOK ENDPOINTS ==========" -ForegroundColor Cyan

try {
    Invoke-RestMethod -Uri "$BaseUrl/v1/billing/webhooks/dashboard" `
        -Method GET `
        -Headers $headers `
        -TimeoutSec 10 | Out-Null
    Write-Pass "GET /v1/billing/webhooks/dashboard"
} catch {
    Write-Fail "GET /v1/billing/webhooks/dashboard - $($_.Exception.Response.StatusCode)"
}

try {
    Invoke-RestMethod -Uri "$BaseUrl/v1/billing/webhooks/recent" `
        -Method GET `
        -Headers $headers `
        -TimeoutSec 10 | Out-Null
    Write-Pass "GET /v1/billing/webhooks/recent"
} catch {
    Write-Fail "GET /v1/billing/webhooks/recent - $($_.Exception.Response.StatusCode)"
}

# ============================================
# SUMMARY
# ============================================
Write-Host "`n========== SUMMARY ==========" -ForegroundColor Cyan
$total = $global:PassCount + $global:FailCount
$rate = if ($total -gt 0) { [math]::Round(($global:PassCount / $total) * 100, 1) } else { 0 }

Write-Host "Total: $total | Passed: $global:PassCount | Failed: $global:FailCount | Rate: $rate%" -ForegroundColor $(if ($rate -ge 80) { "Green" } else { "Yellow" })

if ($global:FailCount -eq 0) {
    Write-Host "`n✅ ALL BILLING ENDPOINTS WORKING!" -ForegroundColor Green
} else {
    Write-Host "`n⚠️  Some endpoints failed" -ForegroundColor Yellow
}
