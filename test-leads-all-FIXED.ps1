# ============================================================================
# LEADFLOW COMPLETE TEST SUITE - LEADS + VENDORS
# Patterns applied from test-all-Settings-Oficial.ps1
# ============================================================================

$BaseUrl = "http://localhost:8081"
$RegisterUrl = "$BaseUrl/auth/register"
$LoginUrl = "$BaseUrl/auth/login"
$MeUrl = "$BaseUrl/auth/me"
$LeadsUrl = "$BaseUrl/api/leads"
$VendorLeadsUrl = "$BaseUrl/api/vendor-leads"
$TenantHeader = "public"
$ProgressPreference = 'SilentlyContinue'

# Global variables
$TestCount = 0
$PassCount = 0
$FailCount = 0
$LoginToken = $null
$CurrentHeaders = @{}

# Colors
$ColorPass = "Green"
$ColorFail = "Red"
$ColorTitle = "Cyan"
$ColorStep = "Yellow"
$ColorInfo = "White"

function Write-Title {
    Write-Host "`nâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•" -ForegroundColor $ColorTitle
    Write-Host "LEADFLOW COMPLETE LEADS + VENDORS TEST SUITE" -ForegroundColor $ColorTitle
    Write-Host "15 Endpoints (Auth + Leads + VendorLeads with Vendors)" -ForegroundColor $ColorTitle
    Write-Host "Patterns Applied from: test-all-Settings-Oficial.ps1" -ForegroundColor $ColorTitle
    Write-Host "â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•`n" -ForegroundColor $ColorTitle
}

function Write-Step {
    param($Number, $Text)
    Write-Host "[$Number] $Text" -ForegroundColor $ColorStep
}

function Write-Success {
    param($Text, $Status)
    Write-Host "    âœ… OK - $Text (HTTP $Status)" -ForegroundColor $ColorPass
    $Global:PassCount++
}

function Write-Fail {
    param($Text, $Status, $Error)
    Write-Host "    âŒ FAIL - $Text (HTTP $Status)" -ForegroundColor $ColorFail
    if ($Error) {
        Write-Host "       Error: $Error" -ForegroundColor $ColorFail
    }
    $Global:FailCount++
}

function Write-Summary {
    $Total = $PassCount + $FailCount
    Write-Host "`nâ•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•" -ForegroundColor $ColorTitle
    Write-Host "TEST SUMMARY - LEADS + VENDORS TEST SUITE" -ForegroundColor $ColorTitle
    Write-Host "â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•" -ForegroundColor $ColorTitle
    Write-Host "Total Tests Run: $Total" -ForegroundColor $ColorInfo
    Write-Host "Passed: $PassCount" -ForegroundColor $ColorPass
    Write-Host "Failed: $FailCount" -ForegroundColor $(if ($FailCount -eq 0) { $ColorPass } else { $ColorFail })
    if ($Total -gt 0) {
        Write-Host "Pass Rate: $([math]::Round(($PassCount/$Total)*100, 2))%" -ForegroundColor $(if ($FailCount -eq 0) { $ColorPass } else { $ColorFail })
    }
    Write-Host "`nâœï¸  TESTS MAPPED FROM: test-all-Settings-Oficial.ps1" -ForegroundColor $ColorTitle
    Write-Host "   - Unified headers pattern (Bearer + X-Tenant-ID)" -ForegroundColor $ColorInfo
    Write-Host "   - Registration & Login setup" -ForegroundColor $ColorInfo
    Write-Host "   - ID storage for dependent tests" -ForegroundColor $ColorInfo
    Write-Host "   - State validation after operations" -ForegroundColor $ColorInfo
    Write-Host "   - Proper try-catch error handling" -ForegroundColor $ColorInfo
    Write-Host "â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•â•`n" -ForegroundColor $ColorTitle
}

Write-Title

# ============================================================================
# TEST 1: HEALTH CHECK (Pattern: test-all-Settings-Oficial.ps1)
# ============================================================================
Write-Step "1" "Health Check - Server Status"
try {
    $healthHeaders = @{
        "X-Tenant-ID" = $TenantHeader
        "Content-Type" = "application/json"
    }
    $response = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -Method Get -Headers $healthHeaders -UseBasicParsing -ErrorAction Stop
    Write-Success "Health" $response.StatusCode
    $Global:TestCount++
} catch {
    Write-Fail "Health" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
    Write-Host "`nâš ï¸ Server is not responding. Cannot continue testing.`n" -ForegroundColor Red
    exit 1
}

# ============================================================================
# TEST 2: REGISTER NEW USER (Pattern: test-all-Settings-Oficial.ps1)
# ============================================================================
Write-Step "2" "Register New User"
$timestamp = Get-Date -Format "yyyyMMddHHmmssfff"
$newEmail = "test_$timestamp@leadflow.dev"
$newPassword = "SecurePassword123!"
$newName = "Test User $timestamp"

try {
    $registerBody = @{
        email = $newEmail
        password = $newPassword
        confirmPassword = $newPassword
        name = $newName
    } | ConvertTo-Json

    $response = Invoke-WebRequest -Uri $RegisterUrl `
        -Method Post `
        -Headers @{ 
            "X-Tenant-Id" = $TenantHeader
            "Content-Type" = "application/json"
        } `
        -Body $registerBody `
        -UseBasicParsing `
        -ErrorAction Stop
    
    Write-Success "Register User" $response.StatusCode
    $Global:TestCount++
} catch {
    Write-Fail "Register User" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
    Write-Host "`nâš ï¸ Cannot continue without successful registration. Stopping tests.`n" -ForegroundColor Red
    exit 1
}

# ============================================================================
# TEST 3: LOGIN USER (Pattern: test-all-Settings-Oficial.ps1)
# ============================================================================
Write-Step "3" "Login User - Setup Headers"

try {
    $loginBody = @{
        email = $newEmail
        password = $newPassword
    } | ConvertTo-Json

    $response = Invoke-WebRequest -Uri $LoginUrl `
        -Method Post `
        -Headers @{ 
            "X-Tenant-Id" = $TenantHeader
            "Content-Type" = "application/json"
        } `
        -Body $loginBody `
        -UseBasicParsing `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    $LoginToken = $data.accessToken
    
    # Setup headers for subsequent requests (PATTERN from Settings)
    $Global:CurrentHeaders = @{
        "X-Tenant-Id" = $TenantHeader
        "Authorization" = "Bearer $LoginToken"
        "Content-Type" = "application/json"
    }
    
    Write-Success "Login & Headers Setup" $response.StatusCode
    $Global:TestCount++
    Write-Host "   Token: $($LoginToken.Substring(0,30))..." -ForegroundColor DarkGray
} catch {
    Write-Fail "Login" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
    Write-Host "`nâš ï¸ Cannot continue without login token. Stopping tests.`n" -ForegroundColor Red
    exit 1
}


# ============================================================================
# TEST 4: GET CURRENT USER PROFILE (Pattern: test-all-Settings-Oficial.ps1)
# ============================================================================
Write-Step "4" "Get Current User Profile"
try {
    $response = Invoke-WebRequest -Uri "$MeUrl" `
        -Method Get `
        -Headers $Global:CurrentHeaders `
        -UseBasicParsing `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    Write-Success "Get User Profile" $response.StatusCode
    if ($data.roles -and $data.roles.Count -gt 0) {
        Write-Host "   User Role: $($data.roles[0].name)" -ForegroundColor DarkGray
    }
    $Global:TestCount++
} catch {
    Write-Fail "Get User Profile" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
}

# ============================================================================
# TEST 5: CREATE STANDARD LEAD (Pattern: test-all-Settings-Oficial.ps1)
# Store ID for subsequent operations
# ============================================================================
Write-Step "5" "Create Standard Lead"
$LeadId = $null

try {
    $randomNum = Get-Random -Minimum 10000 -Maximum 99999
    $leadBody = @{
        name = "Lead Test User"
        email = "lead_$randomNum@test.com"
        phone = "+5511999999999"
    } | ConvertTo-Json

    $response = Invoke-WebRequest -Uri "$LeadsUrl" `
        -Method Post `
        -Headers $Global:CurrentHeaders `
        -Body $leadBody `
        -UseBasicParsing `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    $LeadId = $data.id
    Write-Success "Create Lead" $response.StatusCode
    Write-Host "   Lead ID: $LeadId" -ForegroundColor DarkGray
    $Global:TestCount++
} catch {
    Write-Fail "Create Lead" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
}

# ============================================================================
# TEST 6: GET LEAD BY ID (Pattern: test-all-Settings-Oficial.ps1)
# Validate state after creation
# ============================================================================
Write-Step "6" "Get Lead by ID"
if ($LeadId) {
    try {
        $response = Invoke-WebRequest -Uri "$LeadsUrl/$LeadId" `
            -Method Get `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        $data = $response.Content | ConvertFrom-Json
        Write-Success "Get Lead" $response.StatusCode
        Write-Host "   Status: $($data.status)" -ForegroundColor DarkGray
        $Global:TestCount++
    } catch {
        Write-Fail "Get Lead" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    âš ï¸  Skipped - No Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 7: UPDATE LEAD STATUS (Pattern: test-all-Settings-Oficial.ps1)
# Similar to PATCH /settings - partial update
# ============================================================================
Write-Step "7" "Update Lead Status"
if ($LeadId) {
    try {
        $response = Invoke-WebRequest -Uri "$LeadsUrl/$LeadId/status?status=CONTACTED" `
            -Method Patch `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        $data = $response.Content | ConvertFrom-Json
        Write-Success "Update Lead Status" $response.StatusCode
        Write-Host "   New Status: $($data.status)" -ForegroundColor DarkGray
        $Global:TestCount++
    } catch {
        Write-Fail "Update Lead Status" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    âš ï¸  Skipped - No Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 8: LIST LEADS (Pattern: test-all-Settings-Oficial.ps1)
# Verify list endpoint with pagination
# ============================================================================
Write-Step "8" "List Leads with Pagination"
try {
    $response = Invoke-WebRequest -Uri "$LeadsUrl`?page=0&size=10" `
        -Method Get `
        -Headers $Global:CurrentHeaders `
        -UseBasicParsing `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    Write-Success "List Leads" $response.StatusCode
    if ($data.totalElements) {
        Write-Host "   Total Leads: $($data.totalElements)" -ForegroundColor DarkGray
    }
    $Global:TestCount++
} catch {
    Write-Fail "List Leads" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
}

# ============================================================================
# TEST 9: DELETE LEAD (Pattern: test-all-Settings-Oficial.ps1)
# Delete and validate state
# ============================================================================
Write-Step "9" "Delete Lead"
if ($LeadId) {
    try {
        $response = Invoke-WebRequest -Uri "$LeadsUrl/$LeadId" `
            -Method Delete `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        Write-Success "Delete Lead" $response.StatusCode
        $Global:TestCount++
    } catch {
        Write-Fail "Delete Lead" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    âš ï¸  Skipped - No Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 10: CREATE VENDOR LEAD (Pattern: test-all-Settings-Oficial.ps1)
# This also auto-creates the Vendor through implicit vendor relationship
# ============================================================================
Write-Step "10" "Create Vendor Lead (Auto-create Vendor)"
$VendorLeadId = $null
$VendorId = $null

try {
    $vendorLeadBody = @{
        nomeCompleto = "Maria Silva Consortium"
        whatsapp = "21987654321"
        tipoConsorcio = "VEICULO"
        valorCredito = "100000"
        urgencia = "quero_fechar"
    } | ConvertTo-Json

    $response = Invoke-WebRequest -Uri "$VendorLeadsUrl/leads" `
        -Method Post `
        -Headers $Global:CurrentHeaders `
        -Body $vendorLeadBody `
        -UseBasicParsing `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    $VendorLeadId = $data.id
    if ($data.vendor) {
        $VendorId = $data.vendor.id
    }
    
    Write-Success "Create Vendor Lead" $response.StatusCode
    Write-Host "   Lead ID: $VendorLeadId" -ForegroundColor DarkGray
    if ($VendorId) {
        Write-Host "   Vendor ID: $VendorId (auto-created)" -ForegroundColor DarkGray
    }
    $Global:TestCount++
} catch {
    Write-Fail "Create Vendor Lead" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
}

# ============================================================================
# TEST 11: GET VENDOR LEAD BY ID (Pattern: test-all-Settings-Oficial.ps1)
# Validate state after creation
# ============================================================================
Write-Step "11" "Get Vendor Lead by ID"
if ($VendorLeadId) {
    try {
        $response = Invoke-WebRequest -Uri "$VendorLeadsUrl/$VendorLeadId" `
            -Method Get `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        $data = $response.Content | ConvertFrom-Json
        Write-Success "Get Vendor Lead" $response.StatusCode
        Write-Host "   Status: $($data.stage)" -ForegroundColor DarkGray
        $Global:TestCount++
    } catch {
        Write-Fail "Get Vendor Lead" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    âš ï¸  Skipped - No Vendor Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 12: LIST VENDOR LEADS (Pattern: test-all-Settings-Oficial.ps1)
# ============================================================================
Write-Step "12" "List Vendor Leads with Pagination"
try {
    $response = Invoke-WebRequest -Uri "$VendorLeadsUrl`?page=0&size=10" `
        -Method Get `
        -Headers $Global:CurrentHeaders `
        -UseBasicParsing `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    Write-Success "List Vendor Leads" $response.StatusCode
    if ($data.totalElements) {
        Write-Host "   Total Vendor Leads: $($data.totalElements)" -ForegroundColor DarkGray
    }
    $Global:TestCount++
} catch {
    Write-Fail "List Vendor Leads" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
}

# ============================================================================
# TEST 13: UPDATE VENDOR LEAD STAGE (Pattern: test-all-Settings-Oficial.ps1)
# Similar to PATCH /settings - partial update
# ============================================================================
Write-Step "13" "Update Vendor Lead Stage"
if ($VendorLeadId) {
    try {
        $updateStageBody = @{
            stage = "DISCUSSING"
        } | ConvertTo-Json

        $response = Invoke-WebRequest -Uri "$VendorLeadsUrl/$VendorLeadId/stage" `
            -Method Put `
            -Headers $Global:CurrentHeaders `
            -Body $updateStageBody `
            -UseBasicParsing `
            -ErrorAction Stop
        
        $data = $response.Content | ConvertFrom-Json
        Write-Success "Update Vendor Lead Stage" $response.StatusCode
        Write-Host "   New Stage: $($data.stage)" -ForegroundColor DarkGray
        $Global:TestCount++
    } catch {
        Write-Fail "Update Vendor Lead Stage" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    âš ï¸  Skipped - No Vendor Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 14: DELETE VENDOR LEAD (Pattern: test-all-Settings-Oficial.ps1)
# Delete and then validate deletion
# ============================================================================
Write-Step "14" "Delete Vendor Lead"
if ($VendorLeadId) {
    try {
        $response = Invoke-WebRequest -Uri "$VendorLeadsUrl/$VendorLeadId" `
            -Method Delete `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        Write-Success "Delete Vendor Lead" $response.StatusCode
        $Global:TestCount++
    } catch {
        Write-Fail "Delete Vendor Lead" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    âš ï¸  Skipped - No Vendor Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 15: VALIDATE DELETION (Pattern: test-all-Settings-Oficial.ps1)
# Similar to test 9 in Settings - validate delete succeeded
# ============================================================================
Write-Step "15" "Validate Vendor Lead Deletion"
if ($VendorLeadId) {
    try {
        $response = Invoke-WebRequest -Uri "$VendorLeadsUrl/$VendorLeadId" `
            -Method Get `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        Write-Fail "Validate Deletion" 200 "Should not return success - item was deleted"
        $Global:TestCount++
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.Value__
        if ($statusCode -in @(400, 404)) {
            Write-Success "Validate Deletion" $statusCode
            Write-Host "   Item properly deleted" -ForegroundColor DarkGray
            $Global:TestCount++
        } else {
            Write-Fail "Validate Deletion" $statusCode $_.Exception.Message
            $Global:TestCount++
        }
    }
} else {
    Write-Host "    âš ï¸  Skipped - No Vendor Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# SUMMARY
# ============================================================================
Write-Summary

# Exit with appropriate code
if ($FailCount -gt 0) {
    exit 1
} else {
    exit 0
}

