############################################################
#  ADMIN AUDIT LOGS TEST SUITE
#
#  Tests:
#  - GET /api/admin/audit/security (security audit logs)
#  - GET /api/admin/audit/vendor (vendor audit logs)
############################################################

# ==========================================
# CONFIGURATION
# ==========================================

$BaseURL = "http://localhost:8081/api"
$global:Passed = 0
$global:Failed = 0
$global:TestCount = 0

Write-Host "============================================================"
Write-Host "  ADMIN AUDIT LOGS TEST SUITE"
Write-Host "============================================================"
Write-Host ""
Write-Host "Configuration:"
Write-Host "  Base URL: $BaseURL"
Write-Host "  Timestamp: $(Get-Date -Format 'yyyy-MM-dd HH:mm:ss')"
Write-Host ""

# ==========================================
# UTILITY FUNCTIONS
# ==========================================

function Write-Header {
    param([string]$Text)
    Write-Host ""
    Write-Host "[$($global:TestCount)] $Text" -ForegroundColor Cyan
}

function Write-Success {
    param([string]$Test, [int]$StatusCode)
    $global:Passed++
    $global:TestCount++
    Write-Host "  [OK] $Test (HTTP $StatusCode)" -ForegroundColor Green
}

function Write-Fail {
    param([string]$Test, [int]$StatusCode, [string]$Message)
    $global:Failed++
    $global:TestCount++
    Write-Host "  [FAIL] $Test (HTTP $StatusCode)" -ForegroundColor Red
    if ($Message) { Write-Host "        Error: $Message" -ForegroundColor Red }
}

function Write-Info {
    param([string]$Text)
    Write-Host "         [INFO] $Text" -ForegroundColor Gray
}

# ==========================================
# STEP 1: REGISTER ADMIN USER
# ==========================================

Write-Header "Register Admin User via /auth/register-admin"

$internalSecret = "SUPER_SECRET_KEY_CHANGE_ME"
$adminTenantId = "49c868d1-4da0-420e-8e0e-7b063dcc7390"
$adminEmail = "audit-admin-$(Get-Random)@leadflow.test"
$adminPassword = "AdminTest@123"
$adminToken = $null

try {
    $registerResponse = Invoke-WebRequest -Uri "$BaseURL/auth/register-admin" `
        -Method Post `
        -UseBasicParsing `
        -Headers @{
            "Content-Type" = "application/json"
            "X-Tenant-ID" = $adminTenantId
            "X-Internal-Secret" = $internalSecret
        } `
        -Body (ConvertTo-Json @{
            name = "Audit Admin Test"
            email = $adminEmail
            password = $adminPassword
            confirmPassword = $adminPassword
        }) `
        -ErrorAction Stop

    $registerData = $registerResponse.Content | ConvertFrom-Json
    $adminToken = $registerData.accessToken
    if (!$adminToken) {
        $adminToken = $registerData.token
    }

    Write-Success "Register admin user" 201
    Write-Info "Admin Email: $adminEmail"
    Write-Info "Admin Tenant: $adminTenantId"
    if ($adminToken) {
        Write-Info "Admin Token: $($adminToken.Substring(0, 30))..."
    }
    
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Register admin user" $statusCode "$($_.Exception.Message)"
    exit 1
}

if (!$adminToken) {
    Write-Fail "Get admin token" 0 "No token available from registration"
    exit 1
}

# ==========================================
# STEP 2: QUERY EXISTING AUDIT DATA
# ==========================================

Write-Header "Querying Existing Audit Data"

Write-Info "Ready to query audit logs with admin token"

# ==========================================
# STEP 3: GET SECURITY AUDIT LOGS
# ==========================================

Write-Header "GET /api/admin/audit/security (security audit logs)"

try {
    # Simple query: all security logs
    $auditResponse = Invoke-WebRequest -Uri "$BaseURL/admin/audit/security?page=0&size=20&sort=createdAt,desc" `
        -Method Get `
        -UseBasicParsing `
        -Headers @{
            "Authorization" = "Bearer $adminToken"
            } `
        -ErrorAction Stop

    $auditData = $auditResponse.Content | ConvertFrom-Json

    Write-Success "Get security audit logs (all)" 200
    Write-Info "Total Records: $($auditData.totalElements)"
    Write-Info "Total Pages: $($auditData.totalPages)"
    Write-Info "Records in current page: $($auditData.content.Count)"
    
    if ($auditData.content.Count -gt 0) {
        Write-Info "Latest action: $($auditData.content[0].action)"
        Write-Info "Latest actor: $($auditData.content[0].actorEmail)"
        Write-Info "Result: $($auditData.content[0].success)"
    }
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Get security audit logs (all)" $statusCode $_
}

# Filter by email
try {
    $auditEmailResponse = Invoke-WebRequest -Uri "$BaseURL/admin/audit/security?email=$adminEmail&page=0&size=10&sort=createdAt,desc" `
        -Method Get `
        -UseBasicParsing `
        -Headers @{
            "Authorization" = "Bearer $adminToken"
            } `
        -ErrorAction Stop

    $auditEmailData = $auditEmailResponse.Content | ConvertFrom-Json

    Write-Success "Get security audit logs (filtered by email)" 200
    Write-Info "Admin audit records: $($auditEmailData.totalElements)"
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Get security audit logs (filtered by email)" $statusCode $_
}

# Filter by success status
try {
    $auditSuccessResponse = Invoke-WebRequest -Uri "$BaseURL/admin/audit/security?success=true&page=0&size=10&sort=createdAt,desc" `
        -Method Get `
        -UseBasicParsing `
        -Headers @{
            "Authorization" = "Bearer $adminToken"
            } `
        -ErrorAction Stop

    $auditSuccessData = $auditSuccessResponse.Content | ConvertFrom-Json

    Write-Success "Get security audit logs (filtered by success=true)" 200
    Write-Info "Successful actions: $($auditSuccessData.totalElements)"
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Get security audit logs (filtered by success=true)" $statusCode $_
}

# Filter by failed actions
try {
    $auditFailResponse = Invoke-WebRequest -Uri "$BaseURL/admin/audit/security?success=false&page=0&size=10&sort=createdAt,desc" `
        -Method Get `
        -UseBasicParsing `
        -Headers @{
            "Authorization" = "Bearer $adminToken"
            } `
        -ErrorAction Stop

    $auditFailData = $auditFailResponse.Content | ConvertFrom-Json

    Write-Success "Get security audit logs (filtered by success=false)" 200
    Write-Info "Failed actions: $($auditFailData.totalElements)"
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Get security audit logs (filtered by success=false)" $statusCode $_
}

# ==========================================
# STEP 4: GET VENDOR AUDIT LOGS
# ==========================================

Write-Header "GET /api/admin/audit/vendor (vendor audit logs)"

try {
    # Simple query: all vendor audit logs
    $vendorAuditResponse = Invoke-WebRequest -Uri "$BaseURL/admin/audit/vendor?page=0&size=20&sort=createdAt,desc" `
        -Method Get `
        -UseBasicParsing `
        -Headers @{
            "Authorization" = "Bearer $adminToken"
            } `
        -ErrorAction Stop

    $vendorAuditData = $vendorAuditResponse.Content | ConvertFrom-Json

    Write-Success "Get vendor audit logs (all)" 200
    Write-Info "Total Records: $($vendorAuditData.totalElements)"
    Write-Info "Total Pages: $($vendorAuditData.totalPages)"
    Write-Info "Records in current page: $($vendorAuditData.content.Count)"
    
    if ($vendorAuditData.content.Count -gt 0) {
        Write-Info "Latest action: $($vendorAuditData.content[0].acao)"
        Write-Info "Entity Type: $($vendorAuditData.content[0].entityType)"
    }
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Get vendor audit logs (all)" $statusCode $_
}

# Filter by entity type
try {
    $vendorAuditEntityResponse = Invoke-WebRequest -Uri "$BaseURL/admin/audit/vendor?entityType=VENDOR&page=0&size=10&sort=createdAt,desc" `
        -Method Get `
        -Headers @{
            "Authorization" = "Bearer $adminToken"
            } `
        -ErrorAction Stop

    $vendorAuditEntityData = $vendorAuditEntityResponse.Content | ConvertFrom-Json

    Write-Success "Get vendor audit logs (filtered by entity=VENDOR)" 200
    Write-Info "Vendor entity records: $($vendorAuditEntityData.totalElements)"
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Get vendor audit logs (filtered by entity=VENDOR)" $statusCode $_
}

# ==========================================
# STEP 5: SECURITY - NON-ADMIN CANNOT ACCESS AUDIT LOGS
# ==========================================

Write-Header "SECURITY: Non-admin user cannot access audit logs"

# Create a non-admin user for testing
$nonAdminEmail = "non-admin-audit-$(Get-Random)@leadflow.test"
$nonAdminPassword = "TestPass123!"
$nonAdminToken = $null

try {
    $nonAdminResp = Invoke-WebRequest -Uri "$BaseURL/auth/register" `
        -Method Post `
        -UseBasicParsing `
        -Headers @{"Content-Type" = "application/json"} `
        -Body (ConvertTo-Json @{
            name = "Non Admin User"
            email = $nonAdminEmail
            password = $nonAdminPassword
            confirmPassword = $nonAdminPassword
        }) `
        -ErrorAction Stop

    $nonAdminData = $nonAdminResp.Content | ConvertFrom-Json
    $nonAdminToken = $nonAdminData.accessToken
    if (!$nonAdminToken) {
        $nonAdminToken = $nonAdminData.token
    }
    Write-Info "Created non-admin user for security testing"
} catch {
    Write-Info "Could not create non-admin test user: $_"
}

# Try to access security audit logs as non-admin
try {
    $unauthorizedAuditResp = Invoke-WebRequest -Uri "$BaseURL/admin/audit/security?page=0&size=10" `
        -Method Get `
        -Headers @{
            "Authorization" = "Bearer $nonAdminToken"
            } `
        -ErrorAction Stop

    Write-Fail "Non-admin cannot access security audit (expect 403) (expected to fail)" 200 "Should have been blocked"
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    if ($statusCode -eq 403 -or $statusCode -eq 401) {
        Write-Success "Non-admin blocked from security audit (HTTP $statusCode)" $statusCode
        Write-Info "Security check passed - non-admin access denied"
    } else {
        Write-Fail "Non-admin cannot access security audit (expect 403/401)" $statusCode $_
    }
}

# Try to access vendor audit logs as non-admin
try {
    $unauthorizedVendorResp = Invoke-WebRequest -Uri "$BaseURL/admin/audit/vendor?page=0&size=10" `
        -Method Get `
        -Headers @{
            "Authorization" = "Bearer $nonAdminToken"
            } `
        -ErrorAction Stop

    Write-Fail "Non-admin cannot access vendor audit (expect 403) (expected to fail)" 200 "Should have been blocked"
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    if ($statusCode -eq 403 -or $statusCode -eq 401) {
        Write-Success "Non-admin blocked from vendor audit (HTTP $statusCode)" $statusCode
        Write-Info "Security check passed - non-admin access denied"
    } else {
        Write-Fail "Non-admin cannot access vendor audit (expect 403/401)" $statusCode $_
    }
}

# ==========================================
# TEST SUMMARY
# ==========================================

Write-Host ""
Write-Host "============================================================"
Write-Host "  TEST EXECUTION SUMMARY"
Write-Host "============================================================"
Write-Host ""
Write-Host "Results:"
Write-Host "  Total Tests: $($global:Passed + $global:Failed)"
Write-Host "  Passed: $($global:Passed)" -ForegroundColor Green
Write-Host "  Failed: $($global:Failed)" -ForegroundColor $(if ($global:Failed -gt 0) { "Red" } else { "Green" })
Write-Host "  Pass Rate: $(if ($global:Passed + $global:Failed -gt 0) { [math]::Round(($global:Passed / ($global:Passed + $global:Failed)) * 100) } else { 0 })%"
Write-Host ""

if ($global:Failed -eq 0) {
    Write-Host "[SUCCESS] ALL AUDIT LOG TESTS PASSED!" -ForegroundColor Green
} else {
    Write-Host "[FAILED] $($global:Failed) test(s) failed" -ForegroundColor Red
}

Write-Host "============================================================"




