#!/usr/bin/env pwsh

# Debug: Check if token is being passed correctly

$BaseUrl = "http://localhost:8081"
$TenantHeader = "550e8400-e29b-41d4-a716-446655440000"

# Test data
$testEmail = "debug-user-$(Get-Date -Format 'yyyyMMddHHmmss')@leadflow.dev"
$testPassword = "SecurePass123!@"
$newPassword = "NewSecurePass456!@"

# Step 1: Register
Write-Host "Step 1: Register user..." -ForegroundColor Green

$response = Invoke-RestMethod -Uri "$BaseUrl/auth/register" `
    -Method POST `
    -UseBasicParsing `
    -Headers @{
        "Content-Type" = "application/json"
        "X-Tenant-Id" = $TenantHeader
    } `
    -Body (@{
        name = "Debug User"
        email = $testEmail
        password = $testPassword
        confirmPassword = $testPassword
    } | ConvertTo-Json)

$token = $response.accessToken
Write-Host "[OK] Registered and got token: $($token.Substring(0, 40))..." -ForegroundColor Green

# Step 2: Verify token works with /auth/me
Write-Host "`nStep 2: Test token with GET /auth/me..." -ForegroundColor Green

try {
    $response = Invoke-RestMethod -Uri "$BaseUrl/auth/me" `
        -Method GET `
        -UseBasicParsing `
        -Headers @{
            "Content-Type" = "application/json"
            "X-Tenant-Id" = $TenantHeader
            "Authorization" = "Bearer $token"
        }
    
    Write-Host "[OK]  /auth/me returned 200" -ForegroundColor Green
    Write-Host "      User: $($response.email)" -ForegroundColor Cyan
} catch {
    Write-Host "[FAIL] /auth/me returned: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    exit 1
}

# Step 3: Test change-password with same token
Write-Host "`nStep 3: Test token with POST /auth/change-password..." -ForegroundColor Green

$body = @{
    currentPassword = $testPassword
    newPassword = $newPassword
    confirmPassword = $newPassword
} | ConvertTo-Json -Depth 10

Write-Host "[DEBUG] POST body: $body" -ForegroundColor Cyan
Write-Host "[DEBUG] Authorization header: Bearer $($token.Substring(0, 40))..." -ForegroundColor Cyan

try {
    $response = Invoke-RestMethod -Uri "$BaseUrl/auth/change-password" `
        -Method POST `
        -UseBasicParsing `
        -Headers @{
            "Content-Type" = "application/json"
            "X-Tenant-Id" = $TenantHeader
            "Authorization" = "Bearer $token"
        } `
        -Body $body
    
    Write-Host "[OK] /auth/change-password returned 200-204" -ForegroundColor Green
} catch {
    Write-Host "[FAIL] /auth/change-password returned: $($_.Exception.Response.StatusCode)" -ForegroundColor Red
    Write-Host "[ERROR] $($_.Exception.Message)" -ForegroundColor Red
    
    # Try to read error details
    try {
        $errorBody = $_.Exception.Response.Content | ConvertFrom-Json
        Write-Host "[DEBUG Response] $($errorBody | ConvertTo-Json)" -ForegroundColor Cyan
    } catch {
        Write-Host "[DEBUG] Could not parse error response" -ForegroundColor Gray
    }
}

Write-Host "`n=== DEBUG COMPLETE ===" -ForegroundColor Green
