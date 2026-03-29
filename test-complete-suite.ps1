# ============================================================================
# LEADFLOW COMPLETE TEST SUITE - LEADS + VENDORLEADS
# Final comprehensive test with correct payloads and flow
# ============================================================================

$BaseUrl = "http://localhost:8081/api"
$RegisterUrl = "$BaseUrl/auth/register"
$LoginUrl = "$BaseUrl/auth/login"
$MeUrl = "$BaseUrl/auth/me"
$LeadsUrl = "$BaseUrl/leads"
$VendorLeadsUrl = "$BaseUrl/vendor-leads"
$ProgressPreference = 'SilentlyContinue'

# Global variables
$Global:TestCount = 0
$Global:PassCount = 0
$Global:FailCount = 0
$Global:LoginToken = $null
$Global:TenantHeader = "public"
$Global:CurrentHeaders = @{}

# Colors
$ColorPass = "Green"
$ColorFail = "Red"
$ColorTitle = "Cyan"
$ColorStep = "Yellow"
$ColorInfo = "White"

function Write-Step {
    param($Number, $Text)
    Write-Host "[$Number] $Text" -ForegroundColor $ColorStep
}

function Write-Success {
    param($Text, $Status)
    Write-Host "    ✅ OK - $Text (HTTP $Status)" -ForegroundColor $ColorPass
    $Global:PassCount++
}

function Write-Fail {
    param($Text, $Status, $Error)
    Write-Host "    ❌ FAIL - $Text (HTTP $Status)" -ForegroundColor $ColorFail
    if ($Error) {
        Write-Host "       Error: $Error" -ForegroundColor $ColorFail
    }
    $Global:FailCount++
}

function Write-Summary {
    $Total = $Global:PassCount + $Global:FailCount
    Write-Host "`n════════════════════════════════════════════════════════════" -ForegroundColor $ColorTitle
    Write-Host "TEST SUMMARY" -ForegroundColor $ColorTitle
    Write-Host "════════════════════════════════════════════════════════════" -ForegroundColor $ColorTitle
    Write-Host "Total Tests: $Total" -ForegroundColor $ColorInfo
    Write-Host "Passed: $Global:PassCount" -ForegroundColor $ColorPass
    Write-Host "Failed: $Global:FailCount" -ForegroundColor $(if ($Global:FailCount -eq 0) { $ColorPass } else { $ColorFail })
    if ($Total -gt 0) {
        $passRate = [math]::Round(($Global:PassCount/$Total)*100, 2)
        Write-Host "Pass Rate: $passRate%" -ForegroundColor $(if ($Global:FailCount -eq 0) { $ColorPass } else { $ColorFail })
    }
    Write-Host "════════════════════════════════════════════════════════════`n" -ForegroundColor $ColorTitle
}

Write-Host "`n════════════════════════════════════════════════════════════" -ForegroundColor $ColorTitle
Write-Host "LEADFLOW COMPLETE TEST SUITE" -ForegroundColor $ColorTitle
Write-Host "Leads + VendorLeads Comprehensive Testing" -ForegroundColor $ColorTitle
Write-Host "════════════════════════════════════════════════════════════`n" -ForegroundColor $ColorTitle

# ============================================================================
# TEST 1: REGISTER NEW USER
# ============================================================================
Write-Step "1" "Register New User"
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
            "X-Tenant-ID" = $Global:TenantHeader
            "Content-Type" = "application/json"
        } `
        -Body $registerBody `
        -UseBasicParsing `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    $Global:TenantHeader = $data.tenantId
    Write-Success "Register User" $response.StatusCode
    Write-Host "   Tenant ID: $($Global:TenantHeader)" -ForegroundColor DarkGray
    $Global:TestCount++
} catch {
    Write-Fail "Register User" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
    exit 1
}

# ============================================================================
# TEST 2: LOGIN USER
# ============================================================================
Write-Step "2" "Login User"
try {
    $loginBody = @{
        email = $newEmail
        password = $newPassword
    } | ConvertTo-Json

    $response = Invoke-WebRequest -Uri $LoginUrl `
        -Method Post `
        -Headers @{ 
            "X-Tenant-ID" = $Global:TenantHeader
            "Content-Type" = "application/json"
        } `
        -Body $loginBody `
        -UseBasicParsing `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    $Global:LoginToken = $data.accessToken
    
    $Global:CurrentHeaders = @{
        "X-Tenant-Id" = $Global:TenantHeader
        "Authorization" = "Bearer $Global:LoginToken"
        "Content-Type" = "application/json"
    }
    
    Write-Success "Login" $response.StatusCode
    $Global:TestCount++
} catch {
    Write-Fail "Login" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
    exit 1
}

# ============================================================================
# TEST 3: GET CURRENT USER PROFILE
# ============================================================================
Write-Step "3" "Get Current User Profile"
try {
    $response = Invoke-WebRequest -Uri "$MeUrl" `
        -Method Get `
        -Headers $Global:CurrentHeaders `
        -UseBasicParsing `
        -ErrorAction Stop
    
    Write-Success "Get User Profile" $response.StatusCode
    $Global:TestCount++
} catch {
    Write-Fail "Get User Profile" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
}

# ============================================================================
# TEST 4: CREATE STANDARD LEAD
# ============================================================================
Write-Step "4" "Create Standard Lead"
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
# TEST 5: GET LEAD BY ID
# ============================================================================
Write-Step "5" "Get Lead by ID"
if ($LeadId) {
    try {
        $response = Invoke-WebRequest -Uri "$LeadsUrl/$LeadId" `
            -Method Get `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        Write-Success "Get Lead" $response.StatusCode
        $Global:TestCount++
    } catch {
        Write-Fail "Get Lead" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    ⚠️  Skipped - No Lead ID" -ForegroundColor Yellow
}

# ============================================================================
# TEST 6: UPDATE LEAD STATUS
# ============================================================================
Write-Step "6" "Update Lead Status"
if ($LeadId) {
    try {
        $response = Invoke-WebRequest -Uri "$LeadsUrl/$LeadId/status?status=CONTACTED" `
            -Method Put `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        Write-Success "Update Lead Status" $response.StatusCode
        $Global:TestCount++
    } catch {
        Write-Fail "Update Lead Status" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    ⚠️  Skipped - No Lead ID" -ForegroundColor Yellow
}

# ============================================================================
# TEST 7: LIST LEADS
# ============================================================================
Write-Step "7" "List Leads"
try {
    $response = Invoke-WebRequest -Uri "$LeadsUrl`?page=0&size=10" `
        -Method Get `
        -Headers $Global:CurrentHeaders `
        -UseBasicParsing `
        -ErrorAction Stop
    
    Write-Success "List Leads" $response.StatusCode
    $Global:TestCount++
} catch {
    Write-Fail "List Leads" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
}

# ============================================================================
# TEST 8: DELETE LEAD
# ============================================================================
Write-Step "8" "Delete Lead"
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
    Write-Host "    ⚠️  Skipped - No Lead ID" -ForegroundColor Yellow
}

# ============================================================================
# TEST 9: CREATE VENDOR LEAD (AUTO-CREATES VENDOR)
# ============================================================================
Write-Step "9" "Create Vendor Lead"
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
# TEST 10: GET VENDOR LEAD BY ID
# ============================================================================
Write-Step "10" "Get Vendor Lead by ID"
if ($VendorLeadId) {
    try {
        $response = Invoke-WebRequest -Uri "$VendorLeadsUrl/$VendorLeadId" `
            -Method Get `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        Write-Success "Get Vendor Lead" $response.StatusCode
        $Global:TestCount++
    } catch {
        Write-Fail "Get Vendor Lead" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    ⚠️  Skipped - No Vendor Lead ID" -ForegroundColor Yellow
}

# ============================================================================
# TEST 11: LIST VENDOR LEADS
# ============================================================================
Write-Step "11" "List Vendor Leads"
try {
    $response = Invoke-WebRequest -Uri "$VendorLeadsUrl`?page=0&size=10" `
        -Method Get `
        -Headers $Global:CurrentHeaders `
        -UseBasicParsing `
        -ErrorAction Stop
    
    Write-Success "List Vendor Leads" $response.StatusCode
    $Global:TestCount++
} catch {
    Write-Fail "List Vendor Leads" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
}

# ============================================================================
# TEST 12: UPDATE VENDOR LEAD STAGE
# ============================================================================
Write-Step "12" "Update Vendor Lead Stage"
if ($VendorLeadId) {
    try {
        $response = Invoke-WebRequest -Uri "$VendorLeadsUrl/$VendorLeadId/stage" `
            -Method Put `
            -Headers $Global:CurrentHeaders `
            -Body (@{ stage = "INTERMEDIARIO_ACIONADO" } | ConvertTo-Json) `
            -UseBasicParsing `
            -ErrorAction Stop
        
        Write-Success "Update Vendor Lead Stage" $response.StatusCode
        $Global:TestCount++
    } catch {
        Write-Fail "Update Vendor Lead Stage" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    ⚠️  Skipped - No Vendor Lead ID" -ForegroundColor Yellow
}

# ============================================================================
# TEST 13: DELETE VENDOR LEAD
# ============================================================================
Write-Step "13" "Delete Vendor Lead"
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
    Write-Host "    ⚠️  Skipped - No Vendor Lead ID" -ForegroundColor Yellow
}

# ============================================================================
# TEST 14: VALIDATE VENDOR LEAD DELETION
# ============================================================================
Write-Step "14" "Validate Vendor Lead Deletion"
if ($VendorLeadId) {
    try {
        $response = Invoke-WebRequest -Uri "$VendorLeadsUrl/$VendorLeadId" `
            -Method Get `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction SilentlyContinue
        
        if ($response.StatusCode -eq 404) {
            Write-Success "Vendor Lead Deletion Validated" 404
            $Global:TestCount++
        } else {
            Write-Fail "Vendor Lead Still Exists" $response.StatusCode
            $Global:TestCount++
        }
    } catch {
        if ($_.Exception.Response.StatusCode.Value__ -eq 404) {
            Write-Success "Vendor Lead Deletion Validated" 404
            $Global:TestCount++
        } else {
            Write-Fail "Validation Check Failed" $_.Exception.Response.StatusCode $_.Exception.Message
            $Global:TestCount++
        }
    }
} else {
    Write-Host "    ⚠️  Skipped - No Vendor Lead ID" -ForegroundColor Yellow
}

# ============================================================================
# SUMMARY
# ============================================================================
Write-Summary

if ($Global:FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
