#########################################################
# USERS MANAGEMENT TEST - SIMPLIFIED & DEBUGGED
#########################################################

$BaseURL = "http://localhost:8081/api"
$global:Passed = 0
$global:Failed = 0

Write-Host "`n════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  USERS MANAGEMENT TEST (DEBUGGED)" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════`n" -ForegroundColor Cyan

function Test-Endpoint {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [hashtable]$Headers,
        [string]$Body
    )
    
    Write-Host "`n[TEST] $Name" -ForegroundColor Yellow
    Write-Host "  URL: $Url" -ForegroundColor DarkGray
    Write-Host "  Method: $Method" -ForegroundColor DarkGray
    
    try {
        if ($Body) {
            $response = Invoke-WebRequest -Uri $Url -Method $Method -Headers $Headers -Body $Body -UseBasicParsing -ErrorAction Stop
        } else {
            $response = Invoke-WebRequest -Uri $Url -Method $Method -Headers $Headers -UseBasicParsing -ErrorAction Stop
        }
        
        Write-Host "  ✅ PASS ($($response.StatusCode))" -ForegroundColor Green
        $global:Passed++
        return $response
    }
    catch {
        $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { "?" }
        Write-Host "  ❌ FAIL (HTTP $statusCode)" -ForegroundColor Red
        Write-Host "     Error: $($_.Exception.Message)" -ForegroundColor Red
        $global:Failed++
        return $null
    }
}

# ════════════
# STEP 1: REGISTER NORMAL USER
# ════════════
Write-Host "`n▶ STEP 1: Register normal user" -ForegroundColor Yellow

$normalEmail = "normal-$(Get-Random)@test.local"
$normalPassword = "Test123!@#"

$registerResp = Test-Endpoint `
    -Name "POST /auth/register (normal user)" `
    -Method "Post" `
    -Url "$BaseURL/auth/register" `
    -Headers @{"Content-Type" = "application/json"} `
    -Body (ConvertTo-Json @{
        name = "Normal User"
        email = $normalEmail
        password = $normalPassword
        confirmPassword = $normalPassword
    })

if (!$registerResp) { exit 1 }

$normalData = $registerResp.Content | ConvertFrom-Json
$normalToken = $normalData.accessToken
$tenantId = $normalData.tenantId

Write-Host "  → Token: $($normalToken.Substring(0,25))..." -ForegroundColor DarkGray
Write-Host "  → Tenant: $tenantId" -ForegroundColor DarkGray

# ════════════
# STEP 2: GET /auth/me as normal user
# ════════════
Write-Host "`n▶ STEP 2: Get current user info" -ForegroundColor Yellow

$headers = @{
    "Authorization" = "Bearer $normalToken"
    "Content-Type" = "application/json"
}

$meResp = Test-Endpoint `
    -Name "GET /auth/me (as normal user)" `
    -Method "Get" `
    -Url "$BaseURL/auth/me" `
    -Headers $headers

if (!$meResp) { exit 1 }

$meData = $meResp.Content | ConvertFrom-Json
$userId = $meData.id
$userRole = $meData.role

Write-Host "  → User ID: $userId" -ForegroundColor DarkGray
Write-Host "  → Role: $userRole" -ForegroundColor DarkGray

# ════════════
# STEP 3: GET /users/{id} (get self)
# ════════════
Write-Host "`n▶ STEP 3: Get user by ID (self)" -ForegroundColor Yellow

$getSelfResp = Test-Endpoint `
    -Name "GET /users/{id} (self)" `
    -Method "Get" `
    -Url "$BaseURL/users/$userId" `
    -Headers $headers

if ($getSelfResp) {
    $selfData = $getSelfResp.Content | ConvertFrom-Json
    Write-Host "  → Name: $($selfData.name)" -ForegroundColor DarkGray
    Write-Host "  → Email: $($selfData.email)" -ForegroundColor DarkGray
}

# ════════════
# STEP 4: UPDATE /users/{id} (self)
# ════════════
Write-Host "`n▶ STEP 4: Update user (self)" -ForegroundColor Yellow

$updateBody = @{
    name = "Updated Normal User"
    email = $normalEmail
} | ConvertTo-Json

$updateResp = Test-Endpoint `
    -Name "PUT /users/{id} (self)" `
    -Method "Put" `
    -Url "$BaseURL/users/$userId" `
    -Headers $headers `
    -Body $updateBody

if ($updateResp) {
    $updatedData = $updateResp.Content | ConvertFrom-Json
    Write-Host "  → Updated Name: $($updatedData.name)" -ForegroundColor DarkGray
}

# ════════════
# STEP 5: LIST USERS (should fail - need admin)
# ════════════
Write-Host "`n▶ STEP 5: List users (should fail - normal user)" -ForegroundColor Yellow

$listResp = Test-Endpoint `
    -Name "GET /users (list - normal user)" `
    -Method "Get" `
    -Url "$BaseURL/users" `
    -Headers $headers

if ($listResp) {
    Write-Host "  → Unexpected success! User shouldn't be able to list" -ForegroundColor Red
    $global:Failed++
    $global:Passed--
} else {
    # We expect this to fail with 403
    Write-Host "  → (Expected failure - normal user cannot list)" -ForegroundColor DarkGray
}

# ════════════
# RESULTS
# ════════════
Write-Host "`n════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "  RESULTS" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════" -ForegroundColor Cyan

$total = $global:Passed + $global:Failed
$rate = if ($total -gt 0) { [math]::Round(($global:Passed / $total) * 100, 1) } else { 0 }

Write-Host "`n  ✅ Passed: $($global:Passed)" -ForegroundColor Green
Write-Host "  ❌ Failed: $($global:Failed)" -ForegroundColor Red
Write-Host "  📊 Rate: $rate%" -ForegroundColor Yellow
Write-Host ""
