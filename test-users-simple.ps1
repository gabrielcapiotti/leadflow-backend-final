############################################################
#  USER MANAGEMENT TEST - SIMPLIFIED CORE ENDPOINTS
#
#  Endpoints tested:
#  1. POST /auth/register - Create new user + tenant
#  2. GET /auth/me - Get current user info
#  3. GET /users/{id} - Get user by ID
#  4. PUT /users/{id} - Update user profile
#  5. GET /users - List users (should fail with 403 for normal user)
############################################################

$BaseURL = "http://localhost:8081/api"
$global:Passed = 0
$global:Failed = 0

Write-Host "============================================================"
Write-Host "  USER MANAGEMENT TEST - SIMPLIFIED"
Write-Host "============================================================`n"

# Test 1: Register user
Write-Host "[TEST 1/5] Register User" -ForegroundColor Yellow
try {
    $email = "user-$(Get-Random)@test.com"
    $resp = Invoke-WebRequest -Uri "$BaseURL/auth/register" `
        -Method Post `
        -UseBasicParsing `
        -Headers @{"Content-Type" = "application/json"} `
        -Body (ConvertTo-Json @{
            name = "Test User"
            email = $email
            password = "TestPass123!@"
            confirmPassword = "TestPass123!@"
        }) `
        -ErrorAction Stop
    
    $data = $resp.Content | ConvertFrom-Json
    $token = $data.accessToken
    $tenantId = $data.tenantId
    
    Write-Host "  PASS (201)" -ForegroundColor Green
    Write-Host "  Email: $email" -ForegroundColor Gray
    Write-Host "  Token: $($token.Substring(0,30))..." -ForegroundColor Gray
    $global:Passed++
} catch {
    Write-Host "  FAIL - $($_.Exception.Message)" -ForegroundColor Red
    $global:Failed++
    exit 1
}

# Test 2: Get current user
Write-Host "`n[TEST 2/5] Get Current User (/auth/me)" -ForegroundColor Yellow
try {
    $resp = Invoke-WebRequest -Uri "$BaseURL/auth/me" `
        -Method Get `
        -UseBasicParsing `
        -Headers @{
            "Authorization" = "Bearer $token"
            "Content-Type" = "application/json"
        } `
        -ErrorAction Stop
    
    $meData = $resp.Content | ConvertFrom-Json
    $userId = $meData.id
    
    Write-Host "  PASS (200)" -ForegroundColor Green
    Write-Host "  User ID: $userId" -ForegroundColor Gray
    $global:Passed++
} catch {
    Write-Host "  FAIL - $($_.Exception.Message)" -ForegroundColor Red
    $global:Failed++
    exit 1
}

# Test 3: Get user by ID
Write-Host "`n[TEST 3/5] Get User by ID" -ForegroundColor Yellow
try {
    $resp = Invoke-WebRequest -Uri "$BaseURL/users/$userId" `
        -Method Get `
        -UseBasicParsing `
        -Headers @{
            "Authorization" = "Bearer $token"
            "Content-Type" = "application/json"
        } `
        -ErrorAction Stop
    
    $userData = $resp.Content | ConvertFrom-Json
    
    Write-Host "  PASS (200)" -ForegroundColor Green
    Write-Host "  Name: $($userData.name)" -ForegroundColor Gray
    $global:Passed++
} catch {
    Write-Host "  FAIL - $($_.Exception.Message)" -ForegroundColor Red
    $global:Failed++
}

# Test 4: Update user profile
Write-Host "`n[TEST 4/5] Update User Profile" -ForegroundColor Yellow
try {
    $updateBody = @{
        name = "Updated Name"
        email = $email
    } | ConvertTo-Json
    
    $resp = Invoke-WebRequest -Uri "$BaseURL/users/$userId" `
        -Method Put `
        -UseBasicParsing `
        -Headers @{
            "Authorization" = "Bearer $token"
            "Content-Type" = "application/json"
        } `
        -Body $updateBody `
        -ErrorAction Stop
    
    Write-Host "  PASS (200)" -ForegroundColor Green
    $global:Passed++
} catch {
    Write-Host "  FAIL - $($_.Exception.Message)" -ForegroundColor Red
    $global:Failed++
}

# Test 5: List users (should fail with 403 for normal user)
Write-Host "`n[TEST 5/5] List Users (Should Fail 403)" -ForegroundColor Yellow
try {
    $resp = Invoke-WebRequest -Uri "$BaseURL/users" `
        -Method Get `
        -UseBasicParsing `
        -Headers @{
            "Authorization" = "Bearer $token"
            "Content-Type" = "application/json"
        } `
        -ErrorAction Stop
    
    Write-Host "  UNEXPECTED PASS - Normal user should NOT list all users!" -ForegroundColor Red
    $global:Failed++
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    if ($statusCode -eq 403) {
        Write-Host "  PASS (Correctly blocked with 403)" -ForegroundColor Green
        $global:Passed++
    } else {
        Write-Host "  FAIL - Expected 403, got $statusCode" -ForegroundColor Red
        $global:Failed++
    }
}

# Summary
Write-Host "`n============================================================"
Write-Host "  SUMMARY" -ForegroundColor Cyan
Write-Host "============================================================"
Write-Host "  Passed: $($global:Passed)" -ForegroundColor Green
Write-Host "  Failed: $($global:Failed)" -ForegroundColor Red
Write-Host "  Pass Rate: $(([math]::Round(($global:Passed / 5) * 100, 1)))%" -ForegroundColor Cyan
Write-Host "============================================================"`n
