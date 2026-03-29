# ============================================================================
# LEADFLOW COMPLETE TEST SUITE - LEADS + VENDORS + VENDORLEADS
# Com criação automática de vendor antes de testar VendorLeads
# ============================================================================

$BaseUrl = "http://localhost:8081/api"
$RegisterUrl = "$BaseUrl/auth/register"
$LoginUrl = "$BaseUrl/auth/login"
$MeUrl = "$BaseUrl/auth/me"
$LeadsUrl = "$BaseUrl/leads"
$VendorLeadsUrl = "$BaseUrl/vendor-leads"
$VendorsUrl = "$BaseUrl/vendors"
$Global:TenantHeader = "public"
$ProgressPreference = 'SilentlyContinue'

# Global variables
$Global:TestCount = 0
$Global:PassCount = 0
$Global:FailCount = 0
$Global:LoginToken = $null
$Global:CurrentHeaders = @{}

# Colors
$ColorPass = "Green"
$ColorFail = "Red"
$ColorTitle = "Cyan"
$ColorStep = "Yellow"
$ColorInfo = "White"

function Write-Title {
    Write-Host "`n════════════════════════════════════════════════════════════" -ForegroundColor $ColorTitle
    Write-Host "LEADFLOW COMPLETE LEADS + VENDORLEADS TEST SUITE" -ForegroundColor $ColorTitle
    Write-Host "20 Endpoints (Auth + Leads + Vendors + VendorLeads)" -ForegroundColor $ColorTitle
    Write-Host "Padrão: Criar vendor antes de testar VendorLeads" -ForegroundColor $ColorTitle
    Write-Host "════════════════════════════════════════════════════════════`n" -ForegroundColor $ColorTitle
}

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
    Write-Host "TEST SUMMARY - COMPLETE SUITE" -ForegroundColor $ColorTitle
    Write-Host "════════════════════════════════════════════════════════════" -ForegroundColor $ColorTitle
    Write-Host "Total Tests Run: $Total" -ForegroundColor $ColorInfo
    Write-Host "Passed: $Global:PassCount" -ForegroundColor $ColorPass
    Write-Host "Failed: $Global:FailCount" -ForegroundColor $(if ($Global:FailCount -eq 0) { $ColorPass } else { $ColorFail })
    if ($Total -gt 0) {
        $passRate = [math]::Round(($Global:PassCount/$Total)*100, 2)
        Write-Host "Pass Rate: $passRate%" -ForegroundColor $(if ($Global:FailCount -eq 0) { $ColorPass } else { $ColorFail })
    }
    Write-Host "════════════════════════════════════════════════════════════`n" -ForegroundColor $ColorTitle
}

Write-Host "LEADFLOW COMPLETE TEST SUITE" -ForegroundColor $ColorTitle
Write-Host "22 Endpoints (Auth + Leads + History + Vendors + VendorLeads)" -ForegroundColor $ColorTitle
Write-Host "Padrão: Criar vendor antes de testar VendorLeads" -ForegroundColor $ColorTitle
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
            "X-Tenant-ID" = $TenantHeader
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
    Write-Host "`n⚠️ Cannot continue without successful registration. Stopping tests.`n" -ForegroundColor Red
    exit 1
}

# ============================================================================
# TEST 2: LOGIN USER
# ============================================================================
Write-Step "2" "Login User - Setup Headers"

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
    
    # Setup headers for subsequent requests
    $Global:CurrentHeaders = @{
        "X-Tenant-Id" = $Global:TenantHeader
        "Authorization" = "Bearer $Global:LoginToken"
        "Content-Type" = "application/json"
    }
    
    Write-Success "Login & Headers Setup" $response.StatusCode
    $Global:TestCount++
    Write-Host "   Token: $($Global:LoginToken.Substring(0,30))..." -ForegroundColor DarkGray
} catch {
    Write-Fail "Login" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
    Write-Host "`n⚠️ Cannot continue without login token. Stopping tests.`n" -ForegroundColor Red
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
        
        $data = $response.Content | ConvertFrom-Json
        Write-Success "Get Lead" $response.StatusCode
        Write-Host "   Status: $($data.status)" -ForegroundColor DarkGray
        $Global:TestCount++
    } catch {
        Write-Fail "Get Lead" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    ⚠️  Skipped - No Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 6: UPDATE LEAD STATUS
# ============================================================================
Write-Step "6" "Update Lead Status"
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
    Write-Host "    ⚠️  Skipped - No Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 7: LIST LEADS
# ============================================================================
Write-Step "7" "List Leads with Pagination"
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
# TEST 8: GET LEAD HISTORY (ALL HISTORY RECORDS)
# ============================================================================
Write-Step "8" "Get Lead History - GET /history/leads/{leadId}"
$HistoryRecordId = $null

if ($LeadId) {
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/history/leads/$LeadId" `
            -Method Get `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        $historyData = $response.Content | ConvertFrom-Json
        Write-Success "Get Lead History" $response.StatusCode
        Write-Host "   History Records: $($historyData.Count)" -ForegroundColor DarkGray
        
        # Store first history record ID for next test
        if ($historyData -and $historyData.Count -gt 0 -and $historyData[0].id) {
            $HistoryRecordId = $historyData[0].id
            Write-Host "   First Record ID: $HistoryRecordId" -ForegroundColor DarkGray
        }
        $Global:TestCount++
    } catch {
        Write-Fail "Get Lead History" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    ⚠️  Skipped - No Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 9: GET SPECIFIC HISTORY RECORD
# ============================================================================
Write-Step "9" "Get History Record - GET /history/{historyId}"

if ($HistoryRecordId) {
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/history/$HistoryRecordId" `
            -Method Get `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        $recordData = $response.Content | ConvertFrom-Json
        Write-Success "Get History Record" $response.StatusCode
        Write-Host "   Status: $($recordData.status)" -ForegroundColor DarkGray
        Write-Host "   Changed At: $($recordData.changedAt)" -ForegroundColor DarkGray
        Write-Host "   Changed By: $($recordData.changedBy)" -ForegroundColor DarkGray
        $Global:TestCount++
    } catch {
        Write-Fail "Get History Record" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    ⚠️  Skipped - No History Record ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 10: DELETE LEAD
# ============================================================================
Write-Step "10" "Delete Lead"
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
    Write-Host "    ⚠️  Skipped - No Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 11: CREATE VENDOR LEAD (AUTO-CREATES VENDOR)
# ============================================================================
Write-Step "11" "Create Vendor Lead"
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
# TEST 12: GET VENDOR BY ID
# ============================================================================
Write-Step "12" "Get Vendor by ID"
if ($VendorId) {
    try {
        $response = Invoke-WebRequest -Uri "$VendorsUrl/$VendorId" `
            -Method Get `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        $data = $response.Content | ConvertFrom-Json
        Write-Success "Get Vendor" $response.StatusCode
        Write-Host "   Vendor: $($data.name)" -ForegroundColor DarkGray
        $Global:TestCount++
    } catch {
        Write-Fail "Get Vendor" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    ⚠️  Skipped - No Vendor ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 13: LIST VENDORS
# ============================================================================
Write-Step "13" "List Vendors with Pagination"
try {
    $response = Invoke-WebRequest -Uri "$VendorsUrl`?page=0&size=10" `
        -Method Get `
        -Headers $Global:CurrentHeaders `
        -UseBasicParsing `
        -ErrorAction Stop
    
    $data = $response.Content | ConvertFrom-Json
    Write-Success "List Vendors" $response.StatusCode
    if ($data.totalElements) {
        Write-Host "   Total Vendors: $($data.totalElements)" -ForegroundColor DarkGray
    }
    $Global:TestCount++
} catch {
    Write-Fail "List Vendors" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
}

# ============================================================================
# TEST 14: CREATE VENDOR LEAD
# ============================================================================
Write-Step "14" "Create Vendor Lead"
$VendorLeadId = $null

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
    Write-Success "Create Vendor Lead" $response.StatusCode
    Write-Host "   Lead ID: $VendorLeadId" -ForegroundColor DarkGray
    $Global:TestCount++
} catch {
    Write-Fail "Create Vendor Lead" $_.Exception.Response.StatusCode $_.Exception.Message
    $Global:TestCount++
}

# ============================================================================
# TEST 15: GET VENDOR LEAD BY ID
# ============================================================================
Write-Step "15" "Get Vendor Lead by ID"
if ($VendorLeadId) {
    try {
        $response = Invoke-WebRequest -Uri "$VendorLeadsUrl/$VendorLeadId" `
            -Method Get `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        $data = $response.Content | ConvertFrom-Json
        Write-Success "Get Vendor Lead" $response.StatusCode
        Write-Host "   Stage: $($data.stage)" -ForegroundColor DarkGray
        $Global:TestCount++
    } catch {
        Write-Fail "Get Vendor Lead" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    ⚠️  Skipped - No Vendor Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 16: LIST VENDOR LEADS
# ============================================================================
Write-Step "16" "List Vendor Leads with Pagination"
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
# TEST 17: UPDATE VENDOR LEAD STAGE
# ============================================================================
Write-Step "17" "Update Vendor Lead Stage"
if ($VendorLeadId) {
    try {
        $updateBody = @{
            stage = "CONTATO"
        } | ConvertTo-Json

        $response = Invoke-WebRequest -Uri "$VendorLeadsUrl/$VendorLeadId" `
            -Method Patch `
            -Headers $Global:CurrentHeaders `
            -Body $updateBody `
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
    Write-Host "    ⚠️  Skipped - No Vendor Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 18: DELETE VENDOR LEAD
# ============================================================================
Write-Step "18" "Delete Vendor Lead"
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
    Write-Host "    ⚠️  Skipped - No Vendor Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 19: VALIDATE DELETION
# ============================================================================
Write-Step "19" "Validate Vendor Lead Deletion"
if ($VendorLeadId) {
    try {
        $response = Invoke-WebRequest -Uri "$VendorLeadsUrl/$VendorLeadId" `
            -Method Get `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        Write-Fail "Validate Deletion" $response.StatusCode "Endpoint returned success - lead was not deleted"
        $Global:TestCount++
    } catch {
        if ($_.Exception.Response.StatusCode -eq "404" -or $_.Exception.Response.StatusCode -eq 404) {
            Write-Success "Validate Deletion" "404 (Resource not found)"
            $Global:TestCount++
        } else {
            Write-Fail "Validate Deletion" $_.Exception.Response.StatusCode "Unexpected error"
            $Global:TestCount++
        }
    }
} else {
    Write-Host "    ⚠️  Skipped - No Vendor Lead ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 20: DELETE VENDOR
# ============================================================================
Write-Step "20" "Delete Vendor"
if ($VendorId) {
    try {
        $response = Invoke-WebRequest -Uri "$VendorsUrl/$VendorId" `
            -Method Delete `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        Write-Success "Delete Vendor" $response.StatusCode
        $Global:TestCount++
    } catch {
        Write-Fail "Delete Vendor" $_.Exception.Response.StatusCode $_.Exception.Message
        $Global:TestCount++
    }
} else {
    Write-Host "    ⚠️  Skipped - No Vendor ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# TEST 21: VALIDATE VENDOR DELETION
# ============================================================================
Write-Step "21" "Validate Vendor Deletion"
if ($VendorId) {
    try {
        $response = Invoke-WebRequest -Uri "$VendorsUrl/$VendorId" `
            -Method Get `
            -Headers $Global:CurrentHeaders `
            -UseBasicParsing `
            -ErrorAction Stop
        
        Write-Fail "Validate Vendor Deletion" $response.StatusCode "Endpoint returned success - vendor was not deleted"
        $Global:TestCount++
    } catch {
        if ($_.Exception.Response.StatusCode -eq "404" -or $_.Exception.Response.StatusCode -eq 404) {
            Write-Success "Validate Vendor Deletion" "404 (Resource not found)"
            $Global:TestCount++
        } else {
            Write-Fail "Validate Vendor Deletion" $_.Exception.Response.StatusCode "Unexpected error"
            $Global:TestCount++
        }
    }
} else {
    Write-Host "    ⚠️  Skipped - No Vendor ID from previous test" -ForegroundColor Yellow
}

# ============================================================================
# SUMMARY
# ============================================================================
Write-Summary

# Exit with appropriate code
if ($Global:FailCount -gt 0) {
    exit 1
} else {
    exit 0
}
