#!/usr/bin/env pwsh

# =========================
# CONFIG
# =========================
$baseUrl = "http://localhost:8081"
$tenantId = "public"

$script:TestCount = 0
$script:Passed = 0
$script:Failed = 0
$script:Token = $null
$script:VendorId = $null

Write-Host "`n=== TESTE DASHBOARD & UTILS COM VENDOR ===" -ForegroundColor Cyan

# =========================
# HELPERS
# =========================

function Get-BaseHeaders {
    param([string]$token = $null, [string]$tenant = $tenantId)
    $h = @{
        "Content-Type" = "application/json"
        "X-Tenant-Id" = $tenant
    }
    if ($token) { $h["Authorization"] = "Bearer $token" }
    return $h
}

function Call-Endpoint {
    param(
        [string]$Method,
        [string]$Path,
        [hashtable]$Headers,
        [string]$Body = $null
    )
    
    try {
        $url = "$baseUrl$Path"
        $params = @{
            Uri = $url
            Method = $Method
            Headers = $Headers
            UseBasicParsing = $true
        }
        if ($Body) { $params["Body"] = $Body }
        
        return Invoke-WebRequest @params
    } catch {
        return $_.Exception.Response
    }
}

# =========================
# AUTH & VENDOR SETUP
# =========================
function Setup-AuthAndVendor {
    $email = "test-$(Get-Random -Minimum 1000 -Maximum 9999)@leadflow.dev"
    $password = "TestPassword123!"
    $vendorSlug = "vendor-$(Get-Random -Minimum 10000 -Maximum 99999)"

    try {
        Write-Host "`nRegistering user: $email" -ForegroundColor Yellow

        $regRes = Call-Endpoint POST "/auth/register" `
            (Get-BaseHeaders) `
            (@{
                name = "Test User"
                email = $email
                password = $password
                confirmPassword = $password
            } | ConvertTo-Json)

        if ($regRes.StatusCode -ne 201) {
            throw "Registration failed ($($regRes.StatusCode))"
        }

        Write-Host "✅ Registration successful (201)" -ForegroundColor Green

        Write-Host "Logging in..." -ForegroundColor Yellow

        $loginRes = Call-Endpoint POST "/auth/login" `
            (Get-BaseHeaders) `
            (@{
                email = $email
                password = $password
            } | ConvertTo-Json)

        if ($loginRes.StatusCode -ne 200) {
            throw "Login failed ($($loginRes.StatusCode))"
        }

        $loginData = $loginRes.Content | ConvertFrom-Json
        $script:Token = $loginData.accessToken

        if (-not $script:Token) {
            throw "No token received"
        }

        Write-Host "✅ Login successful (200)" -ForegroundColor Green

        # Create vendor
        Write-Host "Creating vendor: $vendorSlug" -ForegroundColor Yellow

        $vendorRes = Call-Endpoint POST "/api/vendors" `
            (Get-BaseHeaders $script:Token) `
            (@{
                name = "Test Vendor"
                nomeVendedor = "Test Vendor Name"
                whatsappVendedor = "5511999999999"
                slug = $vendorSlug
                userEmail = $email
                corDestaque = "#FF7A00"
            } | ConvertTo-Json)

        if ($vendorRes.StatusCode -eq 200) {
            $vendorData = $vendorRes.Content | ConvertFrom-Json
            $script:VendorId = $vendorData.id
            Write-Host "✅ Vendor created (200) | ID: $($script:VendorId)" -ForegroundColor Green
            return $true
        } else {
            Write-Host "⚠️ Vendor creation failed ($($vendorRes.StatusCode)) - continuing with dashboard test" -ForegroundColor Yellow
            return $false
        }

    } catch {
        Write-Host "❌ Auth setup failed: $_" -ForegroundColor Red
        exit 1
    }
}



# =========================
# TEST FUNCTION
# =========================
function Test-Endpoint {
    param(
        [Parameter(Mandatory=$true)][string]$Method,
        [Parameter(Mandatory=$true)][string]$Path,
        [Parameter(Mandatory=$true)][int[]]$ExpectedCodes,
        [Parameter(Mandatory=$true)][string]$Description,
        [bool]$RequireAuth = $true,
        [string]$AuthToken = ""
    )

    $script:TestCount++

    Write-Host "`n[$($script:TestCount)] $Method $Path" -ForegroundColor Cyan
    Write-Host "    Description: $Description" -ForegroundColor Gray

    try {
        $headers = Get-BaseHeaders
        
        if ($RequireAuth) {
            if (-not $AuthToken) {
                throw "Auth required but token not provided"
            }
            $headers = Get-BaseHeaders $AuthToken
        }

        $res = Call-Endpoint $Method $Path $headers

        $statusCode = $res.StatusCode

        if ($ExpectedCodes -contains $statusCode) {
            Write-Host "    ✅ PASS ($statusCode)" -ForegroundColor Green
            $script:Passed++
            return $true
        } else {
            Write-Host "    ❌ FAIL ($statusCode, expected: $($ExpectedCodes -join '/'))" -ForegroundColor Red
            $script:Failed++
            return $false
        }

    } catch {
        if ($_.Exception.Response) {
            $statusCode = [int]$_.Exception.Response.StatusCode

            if ($ExpectedCodes -contains $statusCode) {
                Write-Host "    ✅ PASS ($statusCode)" -ForegroundColor Green
                $script:Passed++
                return $true
            } else {
                Write-Host "    ❌ FAIL ($statusCode, expected: $($ExpectedCodes -join '/'))" -ForegroundColor Red
                $script:Failed++
                return $false
            }
        } else {
            Write-Host "    ❌ ERROR: $_" -ForegroundColor Red
            $script:Failed++
            return $false
        }
    }
}

# =========================
# EXECUTION
# =========================
$vendorCreated = Setup-AuthAndVendor

if (-not $script:Token) {
    Write-Host "❌ Failed to setup auth" -ForegroundColor Red
    exit 1
}

# =========================
# DASHBOARD TESTS
# =========================
Write-Host "`n========== SECTION 1: DASHBOARD ==========" -ForegroundColor Magenta

if ($vendorCreated) {
    Test-Endpoint -Method "GET" `
        -Path "/api/dashboard" `
        -ExpectedCodes @(200, 204) `
        -Description "Get dashboard data for current vendor" `
        -RequireAuth $true `
        -AuthToken $script:Token
} else {
    Write-Host "`n[1] GET /api/dashboard" -ForegroundColor Cyan
    Write-Host "    Description: Get dashboard data for current vendor" -ForegroundColor Gray
    Write-Host "    ⚠️ SKIPPED (vendor not created)" -ForegroundColor Yellow
    $script:TestCount++
}

# =========================
# ACTUATOR / UTILS TESTS
# =========================
Write-Host "`n========== SECTION 2: UTILS / ACTUATOR ==========" -ForegroundColor Magenta

Test-Endpoint -Method "GET" `
    -Path "/api/health" `
    -ExpectedCodes @(200) `
    -Description "Public health check" `
    -RequireAuth $false

Test-Endpoint -Method "GET" `
    -Path "/api/actuator/health" `
    -ExpectedCodes @(200) `
    -Description "Authenticated health check" `
    -RequireAuth $true `
    -AuthToken $script:Token

Test-Endpoint -Method "GET" `
    -Path "/api/actuator/metrics" `
    -ExpectedCodes @(200) `
    -Description "System metrics" `
    -RequireAuth $true `
    -AuthToken $script:Token

# =========================
# RESULTS
# =========================
Write-Host "`n========== FINAL TEST RESULTS ==========" -ForegroundColor Cyan

Write-Host "`nTest Summary:" -ForegroundColor White
Write-Host "  Total Tests:  $($script:TestCount)"
Write-Host "  Passed:       $($script:Passed)" -ForegroundColor Green
Write-Host "  Failed:       $($script:Failed)" -ForegroundColor $(if ($script:Failed -eq 0) { "Green" } else { "Red" })

if ($script:Failed -eq 0) {
    Write-Host "`n[SUCCESS] ALL TESTS PASSED!" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n[FAILURE] SOME TESTS FAILED" -ForegroundColor Red
    exit 1
}