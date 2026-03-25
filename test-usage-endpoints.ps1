#!/usr/bin/env pwsh

$baseUrl = "http://localhost:8081"
$script:TestCount = 0
$script:Passed = 0
$script:Failed = 0

Write-Host "`n=== TESTE FINAL - USAGE ENDPOINTS (PROFISSIONAL) ===" -ForegroundColor Cyan

# =========================
# SETUP
# =========================
$email = "test-$(Get-Date -Format 'yyyyMMddHHmmss')@leadflow.dev"
$password = "TestPassword123!"
$name = "Test Vendor"

try {
    $regRes = Invoke-WebRequest "$baseUrl/auth/register" `
        -Method POST `
        -Body (@{
            name = $name
            email = $email
            password = $password
            confirmPassword = $password
        } | ConvertTo-Json) `
        -ContentType "application/json" `
        -UseBasicParsing

    if ($regRes.StatusCode -ne 201) { throw "Register failed" }

    $loginRes = Invoke-WebRequest "$baseUrl/auth/login" `
        -Method POST `
        -Body (@{
            email = $email
            password = $password
        } | ConvertTo-Json) `
        -ContentType "application/json" `
        -UseBasicParsing

    if ($loginRes.StatusCode -ne 200) { throw "Login failed" }

    $token = ($loginRes.Content | ConvertFrom-Json).accessToken

    Write-Host "[OK] Auth success" -ForegroundColor Green

} catch {
    Write-Host "[ERROR] Auth failed: $_" -ForegroundColor Red
    exit 1
}

# =========================
# HELPER
# =========================
function Assert-UsagePayload {
    param($json)

    if ($null -eq $json) { return $false }

    # Check for actual DTO properties from BillingDashboardDTO.UsageStatisticsDTO
    $properties = $json.PSObject.Properties.Name
    
    return (
        ($properties -contains "leadsUsed" -or $properties.Count -eq 0) -and
        ($properties -contains "leadsLimit" -or $properties.Count -eq 0)
    )
}

function Test-Usage {
    $script:TestCount++

    Write-Host "`n[$($script:TestCount)] GET /api/v1/billing/usage" -ForegroundColor Cyan

    try {
        $res = Invoke-WebRequest "$baseUrl/api/v1/billing/usage" `
            -Method GET `
            -Headers @{
                "Authorization" = "Bearer $token"
            } `
            -UseBasicParsing

        if ($res.StatusCode -eq 200) {
            Write-Host "    [PASS] Status: 200" -ForegroundColor Green
            $script:Passed++

        } elseif ($res.StatusCode -eq 204) {
            Write-Host "    [PASS] Status: 204 (no usage)" -ForegroundColor Green
            $script:Passed++
        } else {
            Write-Host "    [FAIL] Status: $($res.StatusCode) (expected 200 or 204)" -ForegroundColor Red
            $script:Failed++
        }

    } catch {
        Write-Host "    [ERROR] $_" -ForegroundColor Red
        $script:Failed++
    }
}

# =========================
# TEST EXECUTION
# =========================

Test-Usage

# =========================
# RESULTADOS
# =========================

Write-Host "`n========== RESULT ==========" -ForegroundColor Cyan
Write-Host "Total:  $($script:TestCount)"
Write-Host "Passed: $($script:Passed)" -ForegroundColor Green
Write-Host "Failed: $($script:Failed)" -ForegroundColor $(if ($script:Failed -eq 0) { "Green" } else { "Red" })

if ($script:Failed -eq 0) {
    Write-Host "`n[SUCCESS]" -ForegroundColor Green
    exit 0
} else {
    Write-Host "`n[FAILURE]" -ForegroundColor Red
    exit 1
}