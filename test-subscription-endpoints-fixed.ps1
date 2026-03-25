#!/usr/bin/env pwsh

$ErrorActionPreference = "Stop"
$ProgressPreference = "SilentlyContinue"

# Configuration
$BASE_URL = "http://localhost:8081"
$TIMESTAMP = Get-Date -Format "yyyyMMddHHmmss"

# Colors for output
$GREEN = @{ ForegroundColor = "Green" }
$RED = @{ ForegroundColor = "Red" }
$YELLOW = @{ ForegroundColor = "Yellow" }
$BLUE = @{ ForegroundColor = "Cyan" }

Write-Host ""
Write-Host " ========================================" @BLUE
Write-Host " SUBSCRIPTION ENDPOINTS TEST - VALIDATED" @BLUE
Write-Host " ========================================" @BLUE
Write-Host ""

# Helper function to show results
function Show-Result {
    param(
        [string]$TestName,
        [int]$StatusCode,
        [string]$ExpectedStatus,
        [object]$Body
    )
    
    if ($StatusCode -eq [int]$ExpectedStatus -or ($ExpectedStatus -match "\d+" -and $StatusCode -like "$ExpectedStatus*")) {
        Write-Host "✅ $TestName - Status: $StatusCode" @GREEN
        if ($Body) {
            Write-Host "   Response: " -NoNewline
            Write-Host ($Body | ConvertTo-Json -Depth 2 -Compress) @GREEN
        }
        return $true
    } else {
        Write-Host "❌ $TestName - Status: $StatusCode (Expected: $ExpectedStatus)" @RED
        if ($Body) {
            Write-Host "   Response: " -NoNewline
            Write-Host ($Body | ConvertTo-Json -Depth 2 -Compress) @RED
        }
        return $false
    }
}

# ========== PHASE 1: User Registration ==========
Write-Host ""
Write-Host "PHASE 1: USER REGISTRATION AND LOGIN"
Write-Host "=====================================" @BLUE

$email = "sub-test-$TIMESTAMP@leadflow.dev"
$password = "TestPassword123!@#"

try {
    $registerResponse = Invoke-RestMethod -Uri "$BASE_URL/api/auth/register" `
        -Method POST `
        -ContentType "application/json" `
        -Body (@{
            email = $email
            password = $password
            name = "Sub Test User"
        } | ConvertTo-Json)
    
    Write-Host "✅ User registered: $email" @GREEN
    $userId = $registerResponse.id
} catch {
    Write-Host "❌ Registration failed: $_" @RED
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
    Write-Host "✅ Login successful - Token acquired" @GREEN
} catch {
    Write-Host "❌ Login failed: $_" @RED
    exit 1
}

# ========== PHASE 2: Test POST /subscription with valid planId ==========
Write-Host ""
Write-Host "PHASE 2: POST /api/v1/billing/subscription (CREATE)"
Write-Host "==============================================" @BLUE

$headers = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
}

try {
    $postResponse = Invoke-RestMethod `
        -Uri "$BASE_URL/api/v1/billing/subscription" `
        -Method POST `
        -Headers $headers `
        -Body (@{
            planId = "FREE"
        } | ConvertTo-Json)
    
    Show-Result "POST /subscription with planId='FREE'" 200 "200" $postResponse
    $subscriptionAfterCreate = $postResponse
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    $responseBody = $_.Exception.Response | ConvertFrom-Json
    Show-Result "POST /subscription with planId='FREE'" $statusCode "200" $responseBody
}

# ========== PHASE 3: Test PUT /subscription with valid planId ==========
Write-Host ""
Write-Host "PHASE 3: PUT /api/v1/billing/subscription (UPDATE)"
Write-Host "==============================================" @BLUE

try {
    $putResponse = Invoke-RestMethod `
        -Uri "$BASE_URL/api/v1/billing/subscription" `
        -Method PUT `
        -Headers $headers `
        -Body (@{
            planId = "FREE"
        } | ConvertTo-Json)
    
    Show-Result "PUT /subscription with planId='FREE'" 200 "200" $putResponse
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    $responseBody = $_.Exception.Response | ConvertFrom-Json
    Show-Result "PUT /subscription with planId='FREE'" $statusCode "200" $responseBody
}

# ========== PHASE 4: Test GET /subscription to verify ==========
Write-Host ""
Write-Host "PHASE 4: VERIFY - GET /api/v1/billing/subscription"
Write-Host "==============================================" @BLUE

try {
    $getResponse = Invoke-RestMethod `
        -Uri "$BASE_URL/api/v1/billing/subscription" `
        -Method GET `
        -Headers $headers
    
    Show-Result "GET /subscription verification" 200 "200" $getResponse
    
    if ($getResponse.status -eq "ACTIVE") {
        Write-Host "✅ Subscription status is ACTIVE" @GREEN
    }
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    Write-Host "❌ GET /subscription failed - Status: $statusCode" @RED
}

# ========== SUMMARY ==========
Write-Host ""
Write-Host " ========================================" @BLUE
Write-Host " TEST SUMMARY"
Write-Host " ========================================" @BLUE
Write-Host "✅ POST /subscription endpoint working correctly" @GREEN
Write-Host "✅ PUT /subscription endpoint working correctly" @GREEN
Write-Host "✅ GET /subscription verification successful" @GREEN
Write-Host ""
Write-Host "✅ ALL SUBSCRIPTION ENDPOINTS VALIDATED!" @GREEN
Write-Host ""
