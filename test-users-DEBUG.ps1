########################################################
# USERS MANAGEMENT TEST - WITH ERROR LOGGING
########################################################

$BaseURL = "http://localhost:8081/api"
$global:Passed = 0
$global:Failed = 0

Write-Host "`n[USERS MANAGEMENT TEST]`n" -ForegroundColor Cyan

function Test-Endpoint {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers,
        [string]$Body
    )
    
    Write-Host "[TEST] $Name" -ForegroundColor Yellow
    
    try {
        if ($Body) {
            $response = Invoke-WebRequest -Uri $Url -Method $Method -Headers $Headers -Body $Body -UseBasicParsing -ErrorAction Stop
        } else {
            $response = Invoke-WebRequest -Uri $Url -Method $Method -Headers $Headers -UseBasicParsing -ErrorAction Stop
        }
        
        Write-Host "  PASS (HTTP $($response.StatusCode))" -ForegroundColor Green
        $global:Passed++
        return $response
    }
    catch {
        $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { "?" }
        Write-Host "  FAIL (HTTP $statusCode)" -ForegroundColor Red
        
        # Try to get error body
        try {
            $errorBody = $_.Exception.Response.Content.ToString()
            if ($errorBody.Length -gt 0) {
                Write-Host "     Error: $errorBody" -ForegroundColor Red
            }
        }
        catch { }
        
        $global:Failed++
        return $null
    }
}

# STEP 1: REGISTER NORMAL USER
Write-Host "`n>>> STEP 1: Register normal user" -ForegroundColor Yellow

$timestamp = [int64]((Get-Date).AddSeconds(10).ToUniversalTime() - (New-Object DateTime 1970,1,1,0,0,0,0,([System.DateTimeKind]::Utc))).TotalSeconds
$testEmail = "user$timestamp@example.com"

Write-Host "  Email: $testEmail" -ForegroundColor Gray

$registerBody = @{
    email = $testEmail
    password = "TestPass123!@"
    confirmPassword = "TestPass123!@"
    name = "Test User"
} | ConvertTo-Json

Write-Host "  Payload: $registerBody" -ForegroundColor Gray

$regResp = Test-Endpoint -Name "POST /auth/register" `
    -Method Post `
    -Url "$BaseURL/auth/register" `
    -Headers @{"Content-Type" = "application/json"} `
    -Body $registerBody

if (-not $regResp) {
    Write-Host "`nRegistration failed. Stopping tests."
    Exit
}

$regData = $regResp.Content | ConvertFrom-Json
$userToken = $regData.token

Write-Host "  Token OK" -ForegroundColor Gray

# STEP 2: GET CURRENT USER VIA /auth/me
Write-Host "`n>>> STEP 2: Get current user info" -ForegroundColor Yellow

$meResp = Test-Endpoint -Name "GET /auth/me" `
    -Method Get `
    -Url "$BaseURL/auth/me" `
    -Headers @{
        "Authorization" = "Bearer $userToken"
        "Content-Type" = "application/json"
    }

if (-not $meResp) {
    Write-Host "`nFailed to get current user. Stopping tests."
    Exit
}

$meData = $meResp.Content | ConvertFrom-Json
$userId = $meData.id

Write-Host "  User ID: $userId" -ForegroundColor Gray

# STEP 3: GET USER BY ID
Write-Host "`n>>> STEP 3: Get user by ID" -ForegroundColor Yellow

Test-Endpoint -Name "GET /users/$userId" `
    -Method Get `
    -Url "$BaseURL/users/$userId" `
    -Headers @{
        "Authorization" = "Bearer $userToken"
        "Content-Type" = "application/json"
    } > $null

# STEP 4: UPDATE USER PROFILE
Write-Host "`n>>> STEP 4: Update user profile" -ForegroundColor Yellow

$updateBody = @{
    firstName = "Updated"
    lastName = "Name"
} | ConvertTo-Json

Test-Endpoint -Name "PUT /users/$userId" `
    -Method Put `
    -Url "$BaseURL/users/$userId" `
    -Headers @{
        "Authorization" = "Bearer $userToken"
        "Content-Type" = "application/json"
    } `
    -Body $updateBody > $null

# STEP 5: TRY TO LIST ALL USERS (should fail - normal user)
Write-Host "`n>>> STEP 5: List all users (should fail with 403)" -ForegroundColor Yellow

$listResp = Test-Endpoint -Name "GET /users (should be FORBIDDEN)" `
    -Method Get `
    -Url "$BaseURL/users" `
    -Headers @{
        "Authorization" = "Bearer $userToken"
        "Content-Type" = "application/json"
    }

# SUMMARY
Write-Host "`n================================================" -ForegroundColor Cyan
$total = $global:Passed + $global:Failed
$rate = if ($total -gt 0) { [math]::Round(($global:Passed / $total) * 100, 1) } else { 0 }

Write-Host "  Passed: $($global:Passed)" -ForegroundColor Green
Write-Host "  Failed: $($global:Failed)" -ForegroundColor Red
Write-Host "  Pass Rate: $rate%" -ForegroundColor Yellow
Write-Host "================================================`n" -ForegroundColor Cyan
