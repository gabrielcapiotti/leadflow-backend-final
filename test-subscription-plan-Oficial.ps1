#!/usr/bin/env pwsh

$BaseUrl = "http://localhost:8081"
$TenantHeader = "public"
$ProgressPreference = 'SilentlyContinue'

# =========================
# GLOBALS
# =========================
$global:TestCount = 0
$global:PassedTests = 0
$global:FailedTests = 0
$global:TestResults = @()
$Token = $null

# =========================
# VISUAL
# =========================
function Write-Section {
    param([string]$Title)
    Write-Host "`n$('='*80)" -ForegroundColor Magenta
    Write-Host "  $Title" -ForegroundColor Magenta
    Write-Host "$('='*80)" -ForegroundColor Magenta
}

function Write-Test {
    param($Name)
    $global:TestCount++
    Write-Host "`n[$($global:TestCount)] $Name" -ForegroundColor Yellow
}

function Pass {
    param($msg, $status)
    Write-Host "   [OK] $msg (HTTP $status)" -ForegroundColor Green
    $global:PassedTests++
}

function Fail {
    param($msg, $status, $err)
    Write-Host "   [FAIL] $msg (HTTP $status)" -ForegroundColor Red
    if ($err) { Write-Host "      → $err" -ForegroundColor DarkRed }
    $global:FailedTests++
}

# =========================
# REQUEST CORE (CORRIGIDO)
# =========================
function Request {
    param($method, $endpoint, $body = $null, $auth = $true)

    $headers = @{
        "Content-Type" = "application/json"
        "X-Tenant-Id"  = $TenantHeader
    }

    if ($auth -and $Token) {
        $headers["Authorization"] = "Bearer $Token"
    }

    try {
        $params = @{
            Uri = "$BaseUrl$endpoint"
            Method = $method
            Headers = $headers
            ErrorAction = "Stop"
            UseBasicParsing = $true
        }

        if ($body) {
            $params.Body = ($body | ConvertTo-Json -Depth 10)
        }

        # Usa WebRequest pra pegar status real
        $response = Invoke-WebRequest @params

        $data = $null
        if ($response.Content) {
            try { $data = $response.Content | ConvertFrom-Json } catch {}
        }

        return @{
            ok = $true
            status = $response.StatusCode
            data = $data
        }
    }
    catch {
        $status = 0
        $errorMsg = ""

        try { $status = $_.Exception.Response.StatusCode.value__ } catch {}
        try { $errorMsg = $_.ErrorDetails.Message } catch {}
        if (-not $errorMsg) { $errorMsg = $_.Exception.Message }

        return @{
            ok = $false
            status = $status
            error = $errorMsg
        }
    }
}

# =========================
# ASSERT
# =========================
function Expect {
    param($name, $res, $expected)

    Write-Test $name

    if ($res.status -eq $expected) {
        Pass $name $res.status
    } else {
        Fail $name $res.status $res.error
    }
}

function Expect-Field {
    param($obj, $field, $context)

    if (-not $obj -or -not $obj.$field) {
        Fail "$context → missing field [$field]" 0 ""
    }
}

# =========================
# START
# =========================
Write-Section "BILLING TEST SUITE (ROBUST)"

# =========================
# AUTH
# =========================
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$email = "billing-$timestamp@leadflow.dev"
$password = "Test123!@"

Write-Host "`nAuth..." -ForegroundColor Cyan

$r = Request "POST" "/auth/register" @{
    name = "Billing Test"
    email = $email
    password = $password
    confirmPassword = $password
} $false

if (-not $r.ok) {
    $r = Request "POST" "/auth/login" @{
        email = $email
        password = $password
    } $false
}

if ($r.status -eq 200 -or $r.status -eq 201) {
    Pass "Auth (register/login)" $r.status
} else {
    Fail "Auth (register/login)" $r.status $r.error
}

$Token = $r.data.accessToken
Expect-Field $r.data "accessToken" "Auth"

# =========================
# GET USER
# =========================
$r = Request "GET" "/auth/me"
Expect "Get current user" $r 200
Expect-Field $r.data "id" "User"

$VendorId = $r.data.vendorId

if (-not $VendorId) {
    Write-Host "   [INFO] Vendor não encontrado → criando via vendor-leads..." -ForegroundColor Yellow

    $tmp = Request "POST" "/vendor-leads/leads" @{
        nomeCompleto = "Init Vendor"
        whatsapp = "999999999"
        tipoConsorcio = "VEICULO"
        valorCredito = "100000"
        urgencia = "medio"
    }

    if ($tmp.ok) {
        $VendorId = $tmp.data.vendorId
    }
}

if (-not $VendorId) {
    Write-Host "   [WARN] VendorId ainda null → alguns testes podem falhar" -ForegroundColor Yellow
}

Write-Host "VendorId: $VendorId" -ForegroundColor DarkGray

# =========================
# USER BILLING
# =========================
Expect "Get subscription" (Request "GET" "/billing/subscription") 200
Expect "Get invoices" (Request "GET" "/billing/invoices") 200
Expect "Get payment methods" (Request "GET" "/billing/payment-methods") 200

# =========================
# ADMIN BILLING
# =========================
if ($VendorId) {
    Expect "Admin subscription" (Request "GET" "/api/v1/billing/subscription/$VendorId") 200
    Expect "Admin usage" (Request "GET" "/api/v1/billing/usage/$VendorId") 200
    Expect "Admin dashboard" (Request "GET" "/api/v1/billing/dashboard/$VendorId") 200
    Expect "Stripe events" (Request "GET" "/api/v1/billing/events/$VendorId") 200
}

# =========================
# USER V1
# =========================
Expect "User usage v1" (Request "GET" "/api/v1/billing/usage") 200
Expect "User subscription v1" (Request "GET" "/api/v1/billing/subscription") 200

# =========================
# WEBHOOKS
# =========================
Expect "Webhook failed list" (Request "GET" "/api/billing/webhooks/failed") 200
Expect "Webhook stats" (Request "GET" "/api/billing/webhooks/stats") 200

# =========================
# STRIPE
# =========================
Expect "Checkout invalid (400 expected)" (Request "POST" "/billing/checkout" @{}) 400
Expect "Stripe webhook invalid (401 expected)" (Request "POST" "/stripe/webhook" @{} $false) 401

# =========================
# SUMMARY
# =========================
Write-Section "RESULT"

$total = $global:PassedTests + $global:FailedTests

Write-Host "Passed: $($global:PassedTests)/$total" -ForegroundColor Green
Write-Host "Failed: $($global:FailedTests)" -ForegroundColor Red

if ($global:FailedTests -eq 0) {
    Write-Host "`nALL TESTS PASSED" -ForegroundColor Green
} else {
    Write-Host "`nTESTS FAILED" -ForegroundColor Red
}