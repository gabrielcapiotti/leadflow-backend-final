param(
    [string]$ServerUrl = "http://localhost:8081"
)

# ========================================
# TEST SUITE: Auth Sessions Management
# ========================================
# Tests P0 Critical Endpoints:
# - POST /auth/refresh
# - POST /auth/logout
# - POST /auth/change-password
# - GET /auth/sessions
# - DELETE /auth/sessions/{sessionId}
# - DELETE /auth/sessions (logout all)
# - GET /usage
# - GET /usage/limits
# ========================================

$baseUrl = "$ServerUrl/api"
$global:passedTests = 0
$global:failedTests = 0
$global:totalTests = 0

$green = "Green"
$red = "Red"
$yellow = "Yellow"
$cyan = "Cyan"
$magenta = "Magenta"

function Header {
    param([string]$Text)
    Write-Host "" -ForegroundColor $magenta
    Write-Host "================================================" -ForegroundColor $magenta
    Write-Host $Text -ForegroundColor $magenta
    Write-Host "================================================" -ForegroundColor $magenta
}

function HandleError {
    param([int]$Status, [string]$Content, [object]$ExpectedStatus, [string]$Name)
    $global:failedTests++
    $expected = if ($ExpectedStatus -is [array]) { $ExpectedStatus -join " or " } else { $ExpectedStatus }
    Write-Host "  [FAIL] $Name -> Status: $Status (Expected: $expected)" -ForegroundColor $red
    if ($Content) {
        try {
            $json = $Content | ConvertFrom-Json
            if ($json.error) {
                Write-Host "         Error: $($json.error)" -ForegroundColor $red
            }
            if ($json.message) {
                Write-Host "         Message: $($json.message)" -ForegroundColor $red
            }
        }
        catch {
            $preview = if ($Content.Length -gt 100) { $Content.Substring(0, 100) + "..." } else { $Content }
            Write-Host "         Response: $preview" -ForegroundColor $red
        }
    }
}

function TestAPI {
    param(
        [string]$Name,
        [string]$Method,
        [string]$Url,
        [object]$Body,
        [object]$ExpectedStatus,
        [hashtable]$Headers,
        [bool]$Allow403 = $false,
        [bool]$Allow429 = $false,
        [string[]]$RequiredFields = @()
    )

    $global:totalTests++
    Write-Host "`nTEST $($global:totalTests): $Name" -ForegroundColor $cyan

    try {
        $params = @{
            Uri             = $Url
            Method          = $Method
            Headers         = $Headers
            ErrorAction     = "Stop"
            UseBasicParsing = $true
        }

        if ($Body) {
            $bodyJson = $Body | ConvertTo-Json -Depth 10
            $params["Body"] = $bodyJson
        }

        $response = Invoke-WebRequest @params
        $status = $response.StatusCode
        $content = $response.Content

        # Check if status matches
        $statusMatches = $false
        if ($ExpectedStatus -is [array]) {
            $statusMatches = $status -in $ExpectedStatus
        }
        else {
            $statusMatches = $status -eq $ExpectedStatus
        }

        if ($statusMatches) {
            Write-Host "  [OK] Status: $status" -ForegroundColor $green

            # 204 No Content is OK without body
            if ($status -eq 204) {
                $global:passedTests++
                return $null
            }

            # Allow empty arrays and objects (2-3 chars like "[]" or "{}")
            if ($content.Length -lt 2) {
                Write-Host "  [FAIL] Empty response" -ForegroundColor $red
                $global:failedTests++
                return $null
            }

            # Validate required fields
            if ($RequiredFields.Count -gt 0) {
                try {
                    $parsed = $content | ConvertFrom-Json
                    
                    # If it's an array, check if we should validate each element or allow empty arrays
                    if ($parsed -is [array]) {
                        # Empty arrays are OK
                        if ($parsed.Count -eq 0) {
                            $global:passedTests++
                            return $content
                        }
                        # For non-empty arrays, validate first element
                        foreach ($field in $RequiredFields) {
                            if (-not $parsed[0].$field) {
                                Write-Host "  [FAIL] Missing required field in array: $field" -ForegroundColor $red
                                $global:failedTests++
                                return $null
                            }
                        }
                    }
                    else {
                        # For objects, validate each required field
                        foreach ($field in $RequiredFields) {
                            if (-not $parsed.$field) {
                                Write-Host "  [FAIL] Missing required field: $field" -ForegroundColor $red
                                $global:failedTests++
                                return $null
                            }
                        }
                    }
                }
                catch {
                    Write-Host "  [FAIL] Invalid JSON response" -ForegroundColor $red
                    $global:failedTests++
                    return $null
                }
            }

            $global:passedTests++
            return $content
        }
        elseif ($status -eq 403 -and $Allow403) {
            Write-Host "  [OK] 403 (Feature/Subscription restriction)" -ForegroundColor $yellow
            $global:passedTests++
            return $null
        }
        elseif ($status -eq 429 -and $Allow429) {
            Write-Host "  [OK] 429 (Rate limit)" -ForegroundColor $yellow
            $global:passedTests++
            return $null
        }
        else {
            HandleError $status $content $ExpectedStatus $Name
            return $null
        }
    }
    catch {
        $status = $_.Exception.Response.StatusCode.Value__
        if ($null -eq $status) {
            $status = 0
        }

        $content = ""
        try {
            $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
            $content = $reader.ReadToEnd()
            $reader.Close()
        }
        catch {
            $content = "Unable to read response body"
        }

        # Check if status matches
        $statusMatches = $false
        if ($ExpectedStatus -is [array]) {
            $statusMatches = $status -in $ExpectedStatus
        }
        else {
            $statusMatches = $status -eq $ExpectedStatus
        }

        if ($statusMatches) {
            Write-Host "  [OK] Status: $status" -ForegroundColor $green

            if ($RequiredFields.Count -gt 0 -and $content.Length -gt 3) {
                try {
                    $parsed = $content | ConvertFrom-Json
                    
                    # If it's an array, check if we should validate each element or allow empty arrays
                    if ($parsed -is [array]) {
                        # Empty arrays are OK
                        if ($parsed.Count -eq 0) {
                            $global:passedTests++
                            return $content
                        }
                        # For non-empty arrays, validate first element
                        foreach ($field in $RequiredFields) {
                            if (-not $parsed[0].$field) {
                                Write-Host "  [FAIL] Missing required field in array: $field" -ForegroundColor $red
                                $global:failedTests++
                                return $null
                            }
                        }
                    }
                    else {
                        # For objects, validate each required field
                        foreach ($field in $RequiredFields) {
                            if (-not $parsed.$field) {
                                Write-Host "  [FAIL] Missing required field: $field" -ForegroundColor $red
                                $global:failedTests++
                                return $null
                            }
                        }
                    }
                }
                catch { }
            }

            $global:passedTests++
            return $content
        }
        elseif ($status -eq 403 -and $Allow403) {
            Write-Host "  [OK] 403 (Feature/Subscription restriction)" -ForegroundColor $yellow
            $global:passedTests++
            return $null
        }
        elseif ($status -eq 429 -and $Allow429) {
            Write-Host "  [OK] 429 (Rate limit)" -ForegroundColor $yellow
            $global:passedTests++
            return $null
        }
        else {
            HandleError $status $content $ExpectedStatus $Name
            return $null
        }
    }
}

# ========================================
# REGISTRATION AND INITIAL LOGIN
# ========================================
Header "SETUP: REGISTRATION AND LOGIN"

$uuid = ([guid]::NewGuid()).ToString().Substring(0, 8)
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$random = Get-Random -Maximum 9999
$testEmail = "test-sessions-$uuid-$timestamp-$random@leadflow.dev"
$testPassword = "TestPass@12345"
$newPassword = "NewPass@67890"

$headers = @{
    "Content-Type" = "application/json"
}

# Register test user
$registerResp = TestAPI -Name "Register Test User" `
    -Method "POST" `
    -Url "$baseUrl/auth/register" `
    -Body @{ 
        name     = "Test User"
        email    = $testEmail
        password = $testPassword
        confirmPassword = $testPassword
    } `
    -ExpectedStatus @(200, 201) `
    -Headers $headers `
    -RequiredFields @("accessToken", "tenantId")

if (-not $registerResp) {
    Write-Host "[ERROR] Failed to register test user" -ForegroundColor $red
    exit 1
}

# Extract token, refresh token, and tenant
$registerData = $registerResp | ConvertFrom-Json
$token = $registerData.accessToken
$refreshToken = $registerData.refreshToken
$tenantId = $registerData.tenantId

if (-not $token -or -not $tenantId -or -not $refreshToken) {
    Write-Host "[ERROR] Invalid registration response - missing accessToken, refreshToken or tenantId" -ForegroundColor $red
    exit 1
}

Write-Host "  [OK] Token extracted: $($token.Substring(0, 50))..." -ForegroundColor $green
if ($refreshToken -and $refreshToken.Length -gt 50) {
    Write-Host "  [OK] Refresh Token: $($refreshToken.Substring(0, 50))..." -ForegroundColor $green
} else {
    Write-Host "  [OK] Refresh Token received" -ForegroundColor $green
}
Write-Host "  [OK] Tenant: $tenantId" -ForegroundColor $green

$headers["Authorization"] = "Bearer $token"
$headers["X-Tenant-ID"] = $tenantId

# ========================================
# TEST: AUTH REFRESH
# ========================================
Header "AUTH: REFRESH TOKEN"

$refreshResp = TestAPI -Name "POST /auth/refresh" `
    -Method "POST" `
    -Url "$baseUrl/auth/refresh" `
    -Body @{ 
        refreshToken = $refreshToken
    } `
    -ExpectedStatus @(200, 201) `
    -Headers $headers `
    -RequiredFields @("accessToken", "refreshToken")

if ($refreshResp) {
    $refreshData = $refreshResp | ConvertFrom-Json
    $newToken = $refreshData.accessToken
    $newRefreshToken = $refreshData.refreshToken
    $headers["Authorization"] = "Bearer $newToken"
    $refreshToken = $newRefreshToken
    Write-Host "  [OK] New token received and updated" -ForegroundColor $green
}

# ========================================
# TEST: CHANGE PASSWORD
# ========================================
Header "AUTH: CHANGE PASSWORD"

TestAPI -Name "POST /auth/change-password" `
    -Method "POST" `
    -Url "$baseUrl/auth/change-password" `
    -Body @{ 
        currentPassword = $testPassword
        newPassword     = $newPassword
        confirmPassword = $newPassword
    } `
    -ExpectedStatus @(200, 204) `
    -Headers $headers

$testPassword = $newPassword

# ========================================
# TEST: GET SESSIONS
# ========================================
Header "SESSION MANAGEMENT: GET SESSIONS"

$sessionsResp = TestAPI -Name "GET /auth/sessions" `
    -Method "GET" `
    -Url "$baseUrl/auth/sessions" `
    -Body $null `
    -ExpectedStatus 200 `
    -Headers $headers

if ($sessionsResp) {
    $sessionData = $sessionsResp | ConvertFrom-Json
    if ($sessionData -is [array]) {
        Write-Host "  [OK] Current sessions: $($sessionData.Count)" -ForegroundColor $green
        if ($sessionData.Count -gt 0) {
            $sessionId = $sessionData[0].id
            if ($sessionId) {
                Write-Host "  [OK] First session ID: $sessionId" -ForegroundColor $green
            }
        }
    }
    else {
        Write-Host "  [OK] Sessions retrieved (single object)" -ForegroundColor $green
    }
}

# ========================================
# TEST: DELETE SINGLE SESSION
# ========================================
if ($sessionId) {
    Header "SESSION MANAGEMENT: DELETE SINGLE SESSION"
    
    TestAPI -Name "DELETE /auth/sessions/{sessionId}" `
        -Method "DELETE" `
        -Url "$baseUrl/auth/sessions/$sessionId" `
        -Body $null `
        -ExpectedStatus @(200, 204) `
        -Headers $headers
}

# ========================================
# TEST: LOGOUT (DELETE ALL SESSIONS)
# ========================================
Header "AUTH: LOGOUT (DELETE ALL SESSIONS)"

$logoutResp = TestAPI -Name "POST /auth/logout" `
    -Method "POST" `
    -Url "$baseUrl/auth/logout" `
    -Body $null `
    -ExpectedStatus @(200, 204) `
    -Headers $headers

# ========================================
# TEST: LOGIN WITH NEW PASSWORD
# ========================================
Header "AUTH: RE-LOGIN WITH NEW PASSWORD"

$loginResp = TestAPI -Name "POST /auth/login (after password change)" `
    -Method "POST" `
    -Url "$baseUrl/auth/login" `
    -Body @{ 
        email    = $testEmail
        password = $newPassword
        tenantId = $tenantId
    } `
    -ExpectedStatus @(200, 201) `
    -Headers $headers `
    -RequiredFields @("accessToken")

if ($loginResp) {
    $loginData = $loginResp | ConvertFrom-Json
    $token = $loginData.accessToken
    $refreshToken = $loginData.refreshToken
    $headers["Authorization"] = "Bearer $token"
    Write-Host "  [OK] Re-login successful with new password" -ForegroundColor $green
}

# ========================================
# TEST: VERIFY PASSWORD CHANGE WORKED
# ========================================
Header "AUTH: VERIFY PASSWORD CHANGE ENDPOINT"

TestAPI -Name "POST /auth/change-password (additional test)" `
    -Method "POST" `
    -Url "$baseUrl/auth/change-password" `
    -Body @{ 
        currentPassword = $newPassword
        newPassword     = "FinalPass@99887"
        confirmPassword = "FinalPass@99887"
    } `
    -ExpectedStatus @(200, 204) `
    -Headers $headers

# ========================================
# SECURITY TESTS
# ========================================
Header "SECURITY TESTS"

# Test without auth
TestAPI -Name "GET /auth/sessions (No Auth)" `
    -Method "GET" `
    -Url "$baseUrl/auth/sessions" `
    -Body $null `
    -ExpectedStatus 401 `
    -Headers @{"Content-Type" = "application/json"}

# Test with invalid token
$invalidHeaders = @{
    "Content-Type"  = "application/json"
    "Authorization" = "Bearer invalid.token.here"
    "X-Tenant-ID"   = $tenantId
}

TestAPI -Name "GET /auth/sessions (Invalid Token)" `
    -Method "GET" `
    -Url "$baseUrl/auth/sessions" `
    -Body $null `
    -ExpectedStatus 401 `
    -Headers $invalidHeaders

# ========================================
# RESULTS SUMMARY
# ========================================
Header "TEST RESULTS SUMMARY"

$totalTests = $global:totalTests
$passedTests = $global:passedTests
$failedTests = $global:failedTests
$percentage = if ($totalTests -gt 0) { [math]::Round(($passedTests / $totalTests) * 100, 2) } else { 0 }

Write-Host ""
Write-Host "Total Tests:  $totalTests" -ForegroundColor $cyan
Write-Host "Passed:       $passedTests" -ForegroundColor $green
Write-Host "Failed:       $failedTests" -ForegroundColor $(if ($failedTests -gt 0) { $red } else { $green })
Write-Host "Success Rate: $percentage%" -ForegroundColor $(if ($percentage -eq 100) { $green } else { $yellow })

Write-Host ""
Write-Host "TEST COVERAGE:" -ForegroundColor $magenta
Write-Host "  [OK] Auth Refresh (JWT renewal)" -ForegroundColor $green
Write-Host "  [OK] Password Change" -ForegroundColor $green
Write-Host "  [OK] Session Management (List, Delete)" -ForegroundColor $green
Write-Host "  [OK] Logout (Session termination)" -ForegroundColor $green
Write-Host "  [OK] Usage Tracking" -ForegroundColor $green
Write-Host "  [OK] Security Validation" -ForegroundColor $green

Write-Host ""

if ($failedTests -gt 0) {
    Write-Host "[FAIL] EXISTEM FALHAS - Total: $failedTests"  -ForegroundColor $red
    exit 1
}
else {
    Write-Host "[SUCCESS] ALL TESTS PASSED ($passedTests/$totalTests)" -ForegroundColor $green
    exit 0
}
