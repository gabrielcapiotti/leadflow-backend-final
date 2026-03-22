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

# Global variables - INICIALIZAÇÃO OBRIGATÓRIA
$Global:TestCount = 0
$Global:Passed = 0
$Global:Failed = 0
$LoginToken = $null
$CurrentHeaders = @{}

# Colors
$ColorPass = "Green"
$ColorFail = "Red"
$ColorTitle = "Cyan"
$ColorStep = "Yellow"
$ColorInfo = "White"

# Helper function to get fresh headers (prevents state inconsistency)
function Get-Headers {
    return @{
        "X-Tenant-Id" = $TenantHeader
        "Authorization" = "Bearer $LoginToken"
        "Content-Type" = "application/json"
    }
}

function Write-Title {
    Write-Host "`n════════════════════════════════════════════════════════════" -ForegroundColor $ColorTitle
    Write-Host "LEADFLOW COMPLETE LEADS + VENDORS TEST SUITE" -ForegroundColor $ColorTitle
    Write-Host "15 Endpoints (Auth + Leads + VendorLeads with Vendors)" -ForegroundColor $ColorTitle
    Write-Host "Patterns Applied from: test-all-Settings-Oficial.ps1" -ForegroundColor $ColorTitle
    Write-Host "════════════════════════════════════════════════════════════`n" -ForegroundColor $ColorTitle
}

function Write-Step {
    param($Number, $Text)
    Write-Host "[$Number] $Text" -ForegroundColor $ColorStep
}

function Write-Success {
    param($Text, $Status)
    Write-Host "    ✅ OK - $Text (HTTP $Status)" -ForegroundColor $ColorPass
    $Global:TestCount++
    $Global:Passed++
}

function Write-Fail {
    param($Text, $Status, $Error)
    Write-Host "    ❌ FAIL - $Text (HTTP $Status)" -ForegroundColor $ColorFail
    if ($Error) {
        Write-Host "       Error: $Error" -ForegroundColor $ColorFail
    }
    $Global:TestCount++
    $Global:Failed++
}

function Write-Summary {
    $Total = $Global:Passed + $Global:Failed
    Write-Host "`n════════════════════════════════════════════════════════════" -ForegroundColor $ColorTitle
    Write-Host "TEST SUMMARY - LEADS + VENDORS TEST SUITE" -ForegroundColor $ColorTitle
    Write-Host "════════════════════════════════════════════════════════════" -ForegroundColor $ColorTitle
    Write-Host "Total Tests Run: $Total" -ForegroundColor $ColorInfo
    Write-Host "Passed: $($Global:Passed)" -ForegroundColor $ColorPass
    Write-Host "Failed: $($Global:Failed)" -ForegroundColor $(if ($Global:Failed -eq 0) { $ColorPass } else { $ColorFail })
    if ($Total -gt 0) {
        Write-Host "Pass Rate: $([math]::Round(($Global:Passed/$Total)*100, 2))%" -ForegroundColor $(if ($Global:Failed -eq 0) { $ColorPass } else { $ColorFail })
    }
    Write-Host "`n✍️  TESTS MAPPED FROM: test-all-Settings-Oficial.ps1" -ForegroundColor $ColorTitle
    Write-Host "   - Unified headers pattern (Bearer + X-Tenant-ID)" -ForegroundColor $ColorInfo
    Write-Host "   - Registration & Login setup" -ForegroundColor $ColorInfo
    Write-Host "   - ID storage for dependent tests" -ForegroundColor $ColorInfo
    Write-Host "   - State validation after operations" -ForegroundColor $ColorInfo
    Write-Host "   - Proper try-catch error handling" -ForegroundColor $ColorInfo
    Write-Host "════════════════════════════════════════════════════════════`n" -ForegroundColor $ColorTitle
}

Write-Title

# ============================================================================
# TEST 1: HEALTH CHECK (Pattern: test-all-Settings-Oficial.ps1)
# ============================================================================
Write-Step "1" "Health Check - Server Status"
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/actuator/health" -Method Get -UseBasicParsing -ErrorAction Stop
    Write-Success "Health" $response.StatusCode
} catch {
    Write-Fail "Health" $_.Exception.Response.StatusCode $_.Exception.Message
    Write-Host "`n⚠️ Server is not responding. Cannot continue testing.`n" -ForegroundColor Red
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
} catch {
    Write-Fail "Register User" $_.Exception.Response.StatusCode $_.Exception.Message
    Write-Host "`n⚠️ Cannot continue without successful registration. Stopping tests.`n" -ForegroundColor Red
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
    
    # Validate token before using
    if (-not $LoginToken -or $LoginToken.Length -lt 20) {
        throw "Invalid token returned from login"
    }
    
    Write-Success "Login & Headers Setup" $response.StatusCode
    if ($LoginToken) {
        Write-Host "   Token: $($LoginToken.Substring(0,30))..." -ForegroundColor DarkGray
    }
} catch {
    Write-Fail "Login" $_.Exception.Response.StatusCode $_.Exception.Message
    Write-Host "`n⚠️ Cannot continue without login token. Stopping tests.`n" -ForegroundColor Red
    exit 1
}


# ============================================================================
# TEST 4: GET CURRENT USER PROFILE (Pattern: test-all-Settings-Oficial.ps1)
# ============================================================================
Write-Step "4" "Get Current User Profile"
try {
    $response = Invoke-WebRequest -Uri "$MeUrl" `
        -Method Get `
        -Headers (Get-Headers) `
        -UseBasicParsing `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    Write-Success "Get User Profile" $response.StatusCode
    if ($data.roles -and $data.roles.Count -gt 0) {
        Write-Host "   User Role: $($data.roles[0].name)" -ForegroundColor DarkGray
    }
} catch {
    Write-Fail "Get User Profile" $_.Exception.Response.StatusCode $_.Exception.Message
}

# ============================================================================
# TEST 4B: CREATE VENDOR FOR USER (NEW - REQUIRED FOR VENDOR-LEADS ACCESS)
# This must be done BEFORE accessing vendor-leads endpoints
# ============================================================================
Write-Step "4b" "Create Vendor for User"
$VendorCreated = $false
$VendorId = $null

try {
    # Create unique slug: vendor-YYYYMMDDHHMMSSFFFFF-RANDOMNNNN
    $randomPart = -join ((65..90) + (97..122) + (48..57) | Get-Random -Count 15 | % {[char]$_})
    $combinedSlug = ("vendor-{0}-{1}" -f $timestamp, $randomPart).ToLower()
    $uniqueSlug = $combinedSlug.Substring(0, [Math]::Min(63, $combinedSlug.Length))
    
    $vendorBody = @{
        name = "Vendor $timestamp"
        userEmail = $newEmail
        nomeEmpresa = "Empresa Teste $timestamp"
        nomeVendedor = $newName
        whatsappVendedor = "+5511999999999"
        slug = $uniqueSlug
    } | ConvertTo-Json

    $response = Invoke-WebRequest -Uri "$BaseUrl/vendors" `
        -Method Post `
        -Headers (Get-Headers) `
        -Body $vendorBody `
        -UseBasicParsing `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    $VendorId = $data.id
    $VendorCreated = $true
    
    Write-Success "Create Vendor" $response.StatusCode
    Write-Host "   Vendor ID: $VendorId" -ForegroundColor DarkGray
    Write-Host "   Vendor Email: $($data.userEmail)" -ForegroundColor DarkGray
    Write-Host "   Slug: $uniqueSlug" -ForegroundColor DarkGray
    Write-Host "   Subscription: $($data.subscriptionStatus)" -ForegroundColor DarkGray
} catch {
    Write-Fail "Create Vendor" $_.Exception.Response.StatusCode $_.Exception.Message
    
    # Try to extract error detail
    try {
        $errorObj = $_.Exception.Response.GetResponseStream() | % {$sr = New-Object System.IO.StreamReader($_); $sr.ReadToEnd()}
        if ($errorObj) {
            Write-Host "       Details: $errorObj" -ForegroundColor DarkRed
        }
    } catch {}
    
    Write-Host "`n⚠️ Cannot continue without vendor. Stopping vendor-leads tests.`n" -ForegroundColor Yellow
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
        -Headers (Get-Headers) `
        -Body $leadBody `
        -UseBasicParsing `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    $LeadId = $data.id
    Write-Success "Create Lead" $response.StatusCode
    Write-Host "   Lead ID: $LeadId" -ForegroundColor DarkGray
} catch {
    Write-Fail "Create Lead" $_.Exception.Response.StatusCode $_.Exception.Message
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
            -Headers (Get-Headers) `
            -UseBasicParsing `
            -ErrorAction Stop
        
        $data = $response.Content | ConvertFrom-Json
        Write-Success "Get Lead" $response.StatusCode
        Write-Host "   Status: $($data.status)" -ForegroundColor DarkGray
    } catch {
        Write-Fail "Get Lead" $_.Exception.Response.StatusCode $_.Exception.Message
    }
} else {
    Write-Host "    ⚠️  Skipped - No Lead ID from previous test" -ForegroundColor Yellow
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
            -Headers (Get-Headers) `
            -UseBasicParsing `
            -ErrorAction Stop
        
        $data = $response.Content | ConvertFrom-Json
        if ($data.status -eq "CONTACTED") {
            Write-Success "Update Lead Status" $response.StatusCode
            Write-Host "   New Status: $($data.status)" -ForegroundColor DarkGray
        } else {
            Write-Fail "Status not updated correctly" $response.StatusCode "Expected CONTACTED, got $($data.status)"
        }
    } catch {
        Write-Fail "Update Lead Status" $_.Exception.Response.StatusCode $_.Exception.Message
    }
} else {
    Write-Host "    ⚠️  Skipped - No Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 8: LIST LEADS (Pattern: test-all-Settings-Oficial.ps1)
# Verify list endpoint with pagination
# ============================================================================
Write-Step "8" "List Leads with Pagination"
try {
    $response = Invoke-WebRequest -Uri "$LeadsUrl`?page=0&size=10" `
        -Method Get `
        -Headers (Get-Headers) `
        -UseBasicParsing `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    Write-Success "List Leads" $response.StatusCode
    if ($data.totalElements) {
        Write-Host "   Total Leads: $($data.totalElements)" -ForegroundColor DarkGray
    }
} catch {
    Write-Fail "List Leads" $_.Exception.Response.StatusCode $_.Exception.Message
}

# ============================================================================
# TEST 8B: MULTI-TENANT ISOLATION (LEADS) - SECURITY VALIDATION
# ============================================================================
Write-Step "8b" "Cross-Tenant Isolation (Leads - SECURITY)"

$originalTenant = $TenantHeader
$TenantHeader = "tenant_isolation_test"

try {
    $response = Invoke-WebRequest -Uri "$LeadsUrl" `
        -Method Get `
        -Headers @{
            "X-Tenant-Id" = $TenantHeader
            "Authorization" = "Bearer $LoginToken"
            "Content-Type" = "application/json"
        } `
        -UseBasicParsing `
        -ErrorAction Stop
    
    Write-Fail "SECURITY BREACH - cross-tenant access allowed" $response.StatusCode "Should be 401/403"
} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__
    if ($statusCode -in @(401, 403)) {
        Write-Success "Isolation OK (Leads)" $statusCode
    } else {
        Write-Fail "Unexpected response (should be 401/403)" $statusCode
    }
}

$TenantHeader = $originalTenant

# ============================================================================
# TEST 8c: CROSS-TENANT ACCESS BY ID (CRITICAL SECURITY TEST)
# ========================================================================
Write-Step "8c" "Cross-Tenant Access by ID (SECURITY)"

if ($LeadId) {
    $originalTenant = $TenantHeader
    $TenantHeader = "tenant_attack_test"

    try {
        $response = Invoke-WebRequest -Uri "$LeadsUrl/$LeadId" `
            -Method Get `
            -Headers @{
                "X-Tenant-Id" = $TenantHeader
                "Authorization" = "Bearer $LoginToken"
                "Content-Type" = "application/json"
            } `
            -UseBasicParsing `
            -ErrorAction Stop

        Write-Fail "SECURITY BREACH - Accessed foreign tenant lead" $response.StatusCode "CRITICAL DATA LEAK"

    } catch {
        $statusCode = $_.Exception.Response.StatusCode.Value__

        if ($statusCode -in @(401,403,404)) {
            Write-Success "Isolation OK (ID protected)" $statusCode
        } else {
            Write-Fail "Unexpected response" $statusCode $_.Exception.Message
        }
    }

    $TenantHeader = $originalTenant
}

# ============================================================================
# TEST 8d: CROSS-TENANT LIST ISOLATION (CRITICAL SECURITY TEST)
# ========================================================================
Write-Step "8d" "Cross-Tenant List Isolation (SECURITY)"

$originalTenant = $TenantHeader
$TenantHeader = "tenant_attack_test"

try {
    $response = Invoke-WebRequest -Uri "$LeadsUrl`?page=0&size=10" `
        -Method Get `
        -Headers @{
            "X-Tenant-Id" = $TenantHeader
            "Authorization" = "Bearer $LoginToken"
            "Content-Type" = "application/json"
        } `
        -UseBasicParsing `
        -ErrorAction Stop

    $data = $response.Content | ConvertFrom-Json

    if ($data.content -and $data.content.Count -gt 0) {
        Write-Fail "SECURITY BREACH - Tenant B can see Tenant A data" 200 "DATA LEAK"
    } else {
        Write-Success "Isolation OK (empty list)" 200
    }

} catch {
    $statusCode = $_.Exception.Response.StatusCode.Value__

    if ($statusCode -in @(401,403)) {
        Write-Success "Isolation OK (access blocked)" $statusCode
    } else {
        Write-Fail "Unexpected response" $statusCode $_.Exception.Message
    }
}

$TenantHeader = $originalTenant

# ============================================================================
# TEST 9: DELETE LEAD (Pattern: test-all-Settings-Oficial.ps1)
# Delete and validate state
# ============================================================================
Write-Step "9" "Delete Lead"
if ($LeadId) {
    try {
        $response = Invoke-WebRequest -Uri "$LeadsUrl/$LeadId" `
            -Method Delete `
            -Headers (Get-Headers) `
            -UseBasicParsing `
            -ErrorAction Stop
        
        if ($response.StatusCode -in @(200, 204)) {
            Write-Success "Delete Lead" $response.StatusCode
        } else {
            Write-Fail "Delete Lead" $response.StatusCode "Unexpected status"
        }
    } catch {
        Write-Fail "Delete Lead" $_.Exception.Response.StatusCode $_.Exception.Message
    }
} else {
    Write-Host "    ⚠️  Skipped - No Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 10: CREATE VENDOR LEAD (Pattern: test-all-Settings-Oficial.ps1)
# ============================================================================
Write-Step "10" "Create Vendor Lead"
$VendorLeadId = $null

if (-not $VendorCreated) {
    Write-Host "    ⚠️  Skipped - Vendor was not created in TEST 4b" -ForegroundColor Yellow
} else {
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
            -Headers (Get-Headers) `
            -Body $vendorLeadBody `
            -UseBasicParsing `
            -ErrorAction Stop
        
        $data = $response.Content | ConvertFrom-Json
        $VendorLeadId = $data.id
        
        Write-Success "Create Vendor Lead" $response.StatusCode
        Write-Host "   Lead ID: $VendorLeadId" -ForegroundColor DarkGray
    } catch {
        Write-Fail "Create Vendor Lead" $_.Exception.Response.StatusCode $_.Exception.Message
    }
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
            -Headers (Get-Headers) `
            -UseBasicParsing `
            -ErrorAction Stop
        
        $data = $response.Content | ConvertFrom-Json
        Write-Success "Get Vendor Lead" $response.StatusCode
        Write-Host "   Status: $($data.stage)" -ForegroundColor DarkGray
    } catch {
        Write-Fail "Get Vendor Lead" $_.Exception.Response.StatusCode $_.Exception.Message
    }
} else {
    Write-Host "    ⚠️  Skipped - No Vendor Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 12: LIST VENDOR LEADS (Pattern: test-all-Settings-Oficial.ps1)
# ============================================================================
Write-Step "12" "List Vendor Leads with Pagination"

if (-not $VendorCreated) {
    Write-Host "    ⚠️  Skipped - Vendor was not created in TEST 4b" -ForegroundColor Yellow
} else {
    try {
        $response = Invoke-WebRequest -Uri "$VendorLeadsUrl`?page=0&size=10" `
            -Method Get `
            -Headers (Get-Headers) `
            -UseBasicParsing `
            -ErrorAction Stop
        
        $data = $response.Content | ConvertFrom-Json
        Write-Success "List Vendor Leads" $response.StatusCode
        if ($data.totalElements) {
            Write-Host "   Total Vendor Leads: $($data.totalElements)" -ForegroundColor DarkGray
        }
    } catch {
        Write-Fail "List Vendor Leads" $_.Exception.Response.StatusCode $_.Exception.Message
    }
}

# ============================================================================
# TEST 12b: CROSS-TENANT VENDOR LEAD ACCESS (CRITICAL SECURITY TEST)
# ========================================================================
Write-Step "12b" "Cross-Tenant Vendor Lead Access (SECURITY)"

if ($VendorLeadId) {
    $attackTenant = "tenant_attack_test"

    try {
        $response = Invoke-WebRequest -Uri "$VendorLeadsUrl/$VendorLeadId" `
            -Method Get `
            -Headers @{
                "X-Tenant-Id" = $attackTenant
                "Authorization" = "Bearer $LoginToken"
                "Content-Type" = "application/json"
            } `
            -UseBasicParsing `
            -ErrorAction Stop

        Write-Fail "SECURITY BREACH - Vendor lead exposed cross-tenant" $response.StatusCode "CRITICAL"

    } catch {
        $statusCode = $_.Exception.Response.StatusCode.Value__

        if ($statusCode -in @(401,403,404)) {
            Write-Success "Vendor isolation OK" $statusCode
        } else {
            Write-Fail "Unexpected response" $statusCode $_.Exception.Message
        }
    }
}

# ============================================================================
# TEST 13: UPDATE VENDOR LEAD STAGE (Pattern: test-all-Settings-Oficial.ps1)
# Similar to PATCH /settings - partial update
# ============================================================================
Write-Step "13" "Update Vendor Lead Stage"
if ($VendorLeadId) {
    try {
        $updateStageBody = @{
            stage = "CONTATO"
        } | ConvertTo-Json

        $response = Invoke-WebRequest -Uri "$VendorLeadsUrl/$VendorLeadId/stage" `
            -Method Put `
            -Headers (Get-Headers) `
            -Body $updateStageBody `
            -UseBasicParsing `
            -ErrorAction Stop
        
        $data = $response.Content | ConvertFrom-Json
        if ($data.stage -eq "CONTATO") {
            Write-Success "Update Vendor Lead Stage" $response.StatusCode
            Write-Host "   New Stage: $($data.stage)" -ForegroundColor DarkGray
        } else {
            Write-Fail "Stage not updated correctly" $response.StatusCode "Expected CONTATO, got $($data.stage)"
        }
    } catch {
        Write-Fail "Update Vendor Lead Stage" $_.Exception.Response.StatusCode $_.Exception.Message
    }
} else {
    Write-Host "    ⚠️  Skipped - No Vendor Lead ID from previous test" -ForegroundColor Yellow
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
            -Headers (Get-Headers) `
            -UseBasicParsing `
            -ErrorAction Stop
        
        if ($response.StatusCode -in @(200, 204)) {
            Write-Success "Delete Vendor Lead" $response.StatusCode
        } else {
            Write-Fail "Delete Vendor Lead" $response.StatusCode "Unexpected status"
        }
    } catch {
        Write-Fail "Delete Vendor Lead" $_.Exception.Response.StatusCode $_.Exception.Message
    }
} else {
    Write-Host "    ⚠️  Skipped - No Vendor Lead ID from previous test" -ForegroundColor Yellow
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
            -Headers (Get-Headers) `
            -UseBasicParsing `
            -ErrorAction Stop
        
        Write-Fail "Validate Deletion" 200 "Should not return success - item was deleted"
    } catch {
        $statusCode = $_.Exception.Response.StatusCode.Value__
        # Accept 404 NOT_FOUND as success (proper error response)
        if ($statusCode -eq 404) {
            Write-Success "Validate Deletion" $statusCode
            Write-Host "   Item properly deleted (HTTP 404)" -ForegroundColor DarkGray
        } else {
            Write-Fail "Validate Deletion" $statusCode $_.Exception.Message
        }
    }
} else {
    Write-Host "    ⚠️  Skipped - No Vendor Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# SUMMARY
# ============================================================================
Write-Summary

# Exit with appropriate code
if ($Global:Failed -gt 0) {
    exit 1
} else {
    exit 0
}
