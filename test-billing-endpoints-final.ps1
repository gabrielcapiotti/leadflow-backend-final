#!/usr/bin/env pwsh

$ErrorActionPreference = "SilentlyContinue"
$ProgressPreference = "SilentlyContinue"

# Configuration
$BASE_URL = "http://localhost:8081"
$TIMESTAMP = Get-Date -Format "yyyyMMddHHmmss"

Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "BILLING ENDPOINTS TEST - FINAL VALIDATION" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Configuration:" -ForegroundColor Cyan
Write-Host "  Base URL: $BASE_URL"
Write-Host "  Timestamp: $TIMESTAMP"
Write-Host ""

# ========== PHASE 1: User Registration and Login ==========
Write-Host ""
Write-Host "PHASE 1: USER REGISTRATION AND LOGIN" -ForegroundColor Cyan
Write-Host "=====================================" -ForegroundColor Cyan

$email = "billing-test-$TIMESTAMP@leadflow.dev"
$password = "TestPassword123!@#"

try {
    $registerResponse = Invoke-RestMethod -Uri "$BASE_URL/api/auth/register" `
        -Method POST `
        -ContentType "application/json" `
        -Body (@{
            email = $email
            password = $password
            name = "Billing Test User"
        } | ConvertTo-Json)
    
    Write-Host "✅ User registered: $email" -ForegroundColor Green
    $userId = $registerResponse.id
} catch {
    Write-Host "❌ Registration failed: $_" -ForegroundColor Red
    exit 1
}

# Login to get token
try {
    $loginResponse = Invoke-RestMethod -Uri "$BASE_URL/api/auth/login" `
        -Method POST `
        -ContentType "application/json" `
        -Body (@{
            email = $email
            password = $password
        } | ConvertTo-Json)
    
    $token = $loginResponse.accessToken
    Write-Host "✅ Login successful - Token acquired" -ForegroundColor Green
} catch {
    Write-Host "❌ Login failed: $_" -ForegroundColor Red
    exit 1
}

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

# ========== PHASE 2: Test GET Billing Endpoints ==========
Write-Host ""
Write-Host "PHASE 2: GET BILLING ENDPOINTS (4 endpoints)" -ForegroundColor Cyan
Write-Host "==============================================" -ForegroundColor Cyan

$testsPassed = 0
$testsFailed = 0

# GET /api/v1/billing/subscription
Write-Host ""
Write-Host "TEST 1: GET /api/v1/billing/subscription"
try {
    $response = Invoke-RestMethod -Uri "$BASE_URL/api/v1/billing/subscription" `
        -Method GET `
        -Headers $headers
    Write-Host "✅ Status: 200 - Subscription retrieved" -ForegroundColor Green
    Write-Host "   Plan: $($response.planName), Status: $($response.status)" -ForegroundColor Green
    $testsPassed++
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ Status: $statusCode" -ForegroundColor Red
    $testsFailed++
}

# GET /api/v1/billing/usage
Write-Host ""
Write-Host "TEST 2: GET /api/v1/billing/usage"
try {
    $response = Invoke-RestMethod -Uri "$BASE_URL/api/v1/billing/usage" `
        -Method GET `
        -Headers $headers
    Write-Host "✅ Status: 200 - Usage data retrieved" -ForegroundColor Green
    Write-Host "   Created: $($response.leadsCreated), Limit: $($response.leadsLimit)" -ForegroundColor Green
    $testsPassed++
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ Status: $statusCode" -ForegroundColor Red
    $testsFailed++
}

# GET /api/v1/billing/dashboard
Write-Host ""
Write-Host "TEST 3: GET /api/v1/billing/dashboard"
try {
    $response = Invoke-RestMethod -Uri "$BASE_URL/api/v1/billing/dashboard" `
        -Method GET `
        -Headers $headers
    Write-Host "✅ Status: 200 - Dashboard data retrieved" -ForegroundColor Green
    $testsPassed++
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ Status: $statusCode" -ForegroundColor Red
    $testsFailed++
}

# ========== PHASE 3: Test POST Subscription (NEW ENDPOINT) ==========
Write-Host ""
Write-Host "PHASE 3: POST SUBSCRIPTION (NEWLY IMPLEMENTED)" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan

Write-Host ""
Write-Host "TEST 4: POST /api/v1/billing/subscription"
try {
    $response = Invoke-RestMethod -Uri "$BASE_URL/api/v1/billing/subscription" `
        -Method POST `
        -Headers $headers `
        -Body (@{
            planId = "FREE"
        } | ConvertTo-Json)
    
    Write-Host "✅ Status: 200 - Subscription created/updated" -ForegroundColor Green
    Write-Host "   Plan: $($response.subscription.planName), Status: $($response.subscription.status)" -ForegroundColor Green
    $testsPassed++
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    $errorBody = $_.Exception.Response.Content
    Write-Host "❌ Status: $statusCode" -ForegroundColor Red
    if ($errorBody) {
        Write-Host "   Error: $errorBody" -ForegroundColor Red
    }
    $testsFailed++
}

# ========== PHASE 4: Test PUT Subscription (NEW ENDPOINT) ==========
Write-Host ""
Write-Host "PHASE 4: PUT SUBSCRIPTION (NEWLY IMPLEMENTED)" -ForegroundColor Cyan
Write-Host "===============================================" -ForegroundColor Cyan

Write-Host ""
Write-Host "TEST 5: PUT /api/v1/billing/subscription"
try {
    $response = Invoke-RestMethod -Uri "$BASE_URL/api/v1/billing/subscription" `
        -Method PUT `
        -Headers $headers `
        -Body (@{
            planId = "FREE"
        } | ConvertTo-Json)
    
    Write-Host "✅ Status: 200 - Subscription updated" -ForegroundColor Green
    Write-Host "   Plan: $($response.planName), Status: $($response.status)" -ForegroundColor Green
    $testsPassed++
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    $errorBody = $_.Exception.Response.Content
    Write-Host "❌ Status: $statusCode" -ForegroundColor Red
    if ($errorBody) {
        Write-Host "   Error: $errorBody" -ForegroundColor Red
    }
    $testsFailed++
}

# ========== PHASE 5: Verify Subscription Status ==========
Write-Host ""
Write-Host "PHASE 5: SUBSCRIPTION VERIFICATION" -ForegroundColor Cyan
Write-Host "====================================" -ForegroundColor Cyan

Write-Host ""
Write-Host "TEST 6: Verify subscription is active"
try {
    $response = Invoke-RestMethod -Uri "$BASE_URL/api/v1/billing/subscription" `
        -Method GET `
        -Headers $headers
    
    if ($response.status -eq "ACTIVE") {
        Write-Host "✅ Subscription is ACTIVE as expected" -ForegroundColor Green
        $testsPassed++
    } else {
        Write-Host "⚠️  Subscription status is $($response.status) (expected ACTIVE)" -ForegroundColor Yellow
        $testsFailed++
    }
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ Verification failed - Status: $statusCode" -ForegroundColor Red
    $testsFailed++
}

# ========== SUMMARY ==========
Write-Host ""
Write-Host "========================================" -ForegroundColor Cyan
Write-Host "TEST SUMMARY" -ForegroundColor Cyan
Write-Host "========================================" -ForegroundColor Cyan
Write-Host ""
Write-Host "Total Tests: 6" -ForegroundColor White
Write-Host "Passed: $testsPassed" -ForegroundColor Green
Write-Host "Failed: $testsFailed" -ForegroundColor $(if ($testsFailed -gt 0) { "Red" } else { "Green" })
Write-Host ""

if ($testsFailed -eq 0) {
    Write-Host "✅ ALL TESTS PASSED!" -ForegroundColor Green
    Write-Host ""
    Write-Host "✅ POST /subscription endpoint working correctly" -ForegroundColor Green
    Write-Host "✅ PUT /subscription endpoint working correctly" -ForegroundColor Green
    Write-Host "✅ Subscription management fully operational" -ForegroundColor Green
} else {
    Write-Host "❌ SOME TESTS FAILED - Review above for details" -ForegroundColor Red
}

Write-Host ""
Write-Host "Completed at: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')" -ForegroundColor Cyan
Write-Host ""
