#!/usr/bin/env pwsh

# Colors
$green = 'Green'
$red = 'Red'
$yellow = 'Yellow'
$cyan = 'Cyan'

# Config
$baseUrl = "http://localhost:8081"
$apiPath = "/api/users"
$tenantId = "public"

Write-Host ""
Write-Host "===========================================" -ForegroundColor $cyan
Write-Host "TESTING USER ENDPOINTS" -ForegroundColor $cyan
Write-Host "===========================================" -ForegroundColor $cyan
Write-Host ""

$tenantHeaders = @{
    "X-Tenant-ID" = $tenantId
    "Content-Type" = "application/json"
}

# Step 1: Login as Admin
Write-Host "Step 1: Login as Admin" -ForegroundColor $cyan
$loginPayload = @{
    email = "admin.test@leadflow.com"
    password = "AdminPassword123!"
} | ConvertTo-Json

try {
    $loginResponse = Invoke-WebRequest -Uri "$baseUrl/auth/login" `
        -Method POST `
        -Headers $tenantHeaders `
        -Body $loginPayload `
        -UseBasicParsing `
        -ErrorAction Stop

    $loginData = $loginResponse.Content | ConvertFrom-Json
    $token = $loginData.accessToken
    $userId = $loginData.userId
    
    Write-Host "[OK] Login successful as admin.test@leadflow.com" -ForegroundColor $green
    Write-Host "    Token: $($token.Substring(0, 20))..." -ForegroundColor gray
} catch {
    Write-Host "[ERROR] Login failed: $($_.Exception.Message)" -ForegroundColor $red
    exit 1
}

$authHeaders = @{
    "Authorization" = "Bearer $token"
    "X-Tenant-ID" = $tenantId
    "Content-Type" = "application/json"
}

Write-Host ""

# Step 2: List users (GET /api/users)
Write-Host "Step 2: GET /api/users - List users" -ForegroundColor $cyan
try {
    $listResponse = Invoke-WebRequest -Uri "$baseUrl$apiPath" `
        -Method GET `
        -Headers $authHeaders `
        -UseBasicParsing `
        -ErrorAction Stop

    $listData = $listResponse.Content | ConvertFrom-Json
    Write-Host "[OK] Status: $($listResponse.StatusCode)" -ForegroundColor $green
    Write-Host "    Total: $($listData.totalElements) | Page: $($listData.number)/$($listData.totalPages)" -ForegroundColor gray
} catch {
    $code = $_.Exception.Response.StatusCode
    if ($code -eq "Forbidden" -or $code -eq 403) {
        Write-Host "[WARN] Access Forbidden (403) - Requires ADMIN role" -ForegroundColor $yellow
        Write-Host "    Current user may not have admin permissions" -ForegroundColor gray
    } else {
        Write-Host "[ERROR] Status: $code" -ForegroundColor $red
    }
}

Write-Host ""

# Step 3: Get user by ID (GET /api/users/{id})
Write-Host "Step 3: GET /api/users/{id} - Get user by ID" -ForegroundColor $cyan
if ($userId) {
    try {
        $getResponse = Invoke-WebRequest -Uri "$baseUrl$apiPath/$userId" `
            -Method GET `
            -Headers $authHeaders `
            -UseBasicParsing `
            -ErrorAction Stop

        $userData = $getResponse.Content | ConvertFrom-Json
        Write-Host "[OK] Status: $($getResponse.StatusCode)" -ForegroundColor $green
        Write-Host "    ID: $($userData.id)" -ForegroundColor gray
        Write-Host "    Name: $($userData.name)" -ForegroundColor gray
        Write-Host "    Email: $($userData.email)" -ForegroundColor gray
    } catch {
        $code = $_.Exception.Response.StatusCode
        Write-Host "[ERROR] Status: $code" -ForegroundColor $red
    }
} else {
    Write-Host "[SKIP] No user ID available" -ForegroundColor $yellow
}

Write-Host ""
Write-Host "===========================================" -ForegroundColor $cyan
Write-Host "USER ENDPOINTS TEST - COMPLETE" -ForegroundColor $cyan
Write-Host "===========================================" -ForegroundColor $cyan
Write-Host ""
Write-Host "Admin User Seed Created:" -ForegroundColor $cyan
Write-Host "Email: admin.test@leadflow.com" -ForegroundColor gray
Write-Host "Password: AdminPassword123!" -ForegroundColor gray
Write-Host ""
