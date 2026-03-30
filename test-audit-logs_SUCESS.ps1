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
# STEP 1: REGISTER AND LOGIN ADMIN USER
# ==========================================

Write-Header "Register and Login Admin User"

$adminEmail = "admin-audit-test-$(Get-Random)@leadflow.test"
$adminPassword = "SecurePassword123!"
$adminToken = $null
$adminTenantId = $null

# Register the admin user
try {
    $registerResponse = Invoke-WebRequest -Uri "$BaseURL/auth/register" `
        -Method Post `
        -UseBasicParsing `
        -Headers @{"Content-Type" = "application/json"} `
        -Body (ConvertTo-Json @{
            name = "Admin Audit Test"
            email = $adminEmail
            password = $adminPassword
            confirmPassword = $adminPassword
        }) `
        -ErrorAction Stop

    $registerData = $registerResponse.Content | ConvertFrom-Json
    $adminTenantId = $registerData.tenantId
    
    # Extract token - field is 'accessToken' not 'token'
    if ($registerData.accessToken) {
        $adminToken = $registerData.accessToken
    } elseif ($registerData.token) {
        $adminToken = $registerData.token
    }

    Write-Success "Register admin user" 201
    Write-Info "Admin Tenant: $adminTenantId"
    if ($adminToken) {
        Write-Info "Admin Token: $($adminToken.Substring(0, 30))..."
    }
    
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    Write-Fail "Register admin user" $statusCode "$($_.Exception.Message)"
    exit 1
}

# If no token from registration, try login
if (!$adminToken) {
    try {
        $loginResponse = Invoke-WebRequest -Uri "$BaseURL/auth/login" `
            -Method Post `
            -UseBasicParsing `
            -Headers @{"Content-Type" = "application/json"} `
            -Body (ConvertTo-Json @{
                email = $adminEmail
                password = $adminPassword
                deviceFingerprint = "audit-test"
            }) `
            -ErrorAction Stop

        $loginData = $loginResponse.Content | ConvertFrom-Json
        $adminToken = $loginData.accessToken
        if (!$adminToken) {
            $adminToken = $loginData.token
        }
        
    } catch {
        Write-Info "Login attempt: credentials may need to propagate"
        Start-Sleep -Seconds 2
    }
}

if (!$adminToken) {
    Write-Fail "Get admin token" 0 "No token available from registration or login"
    # Continue anyway to see rest of response structure
}

# ==========================================
# STEP 2: GENERATE AUDIT ACTIVITY
# ==========================================

Write-Header "Generate Audit Activity (Login attempts + User creation)"

# Create several users to generate audit logs
for ($i = 1; $i -le 2; $i++) {
    try {
        $testEmail = "audit-user-$i-$(Get-Date -Format 'yyMMddHHmmss')@leadflow.test"
        $registerResp = Invoke-WebRequest -Uri "$BaseURL/auth/register" `
            -Method Post `
            -UseBasicParsing `
            -Headers @{"Content-Type" = "application/json"} `
            -Body (ConvertTo-Json @{
                name = "Audit Test User $i"
                email = $testEmail
                password = "TestPass123!"
                confirmPassword = "TestPass123!"
            }) `
            -ErrorAction Stop

        Write-Info "Created test user $i for audit activity"
    } catch {
        Write-Info "User creation may have generated audit logs"
    }
}

# Attempt failed login (should generate security audit)
try {
    $failedLoginResp = Invoke-WebRequest -Uri "$BaseURL/auth/login" `
        -Method Post `
        -UseBasicParsing `
        -Headers @{"Content-Type" = "application/json"; "X-Tenant-ID" = $adminTenantId} `
        -Body (ConvertTo-Json @{
            email = "nonexistent@leadflow.test"
            password = "WrongPassword"
            deviceFingerprint = "test-device"
        }) `
        -ErrorAction Stop
} catch {
    Write-Info "Failed login attempt recorded in security audit logs"
}

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
            "X-Tenant-ID" = $adminTenantId
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
            "X-Tenant-ID" = $adminTenantId
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
            "X-Tenant-ID" = $adminTenantId
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
            "X-Tenant-ID" = $adminTenantId
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
            "X-Tenant-ID" = $adminTenantId
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
            "X-Tenant-ID" = $adminTenantId
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

# Create a non-admin user
$nonAdminEmail = "non-admin-audit@leadflow.test"
try {
    $nonAdminResp = Invoke-WebRequest -Uri "$BaseURL/auth/register" `
        -Method Post `
        -Headers @{"Content-Type" = "application/json"} `
        -Body (ConvertTo-Json @{
            name = "Non Admin User"
            email = $nonAdminEmail
            password = "TestPass123!"
            confirmPassword = "TestPass123!"
        }) `
        -ErrorAction Stop

    $nonAdminData = $nonAdminResp.Content | ConvertFrom-Json
    $nonAdminToken = $nonAdminData.token
} catch {
    Write-Info "Failed to create non-admin user for security test"
}

# Try to access security audit logs as non-admin
try {
    $unauthorizedAuditResp = Invoke-WebRequest -Uri "$BaseURL/admin/audit/security?page=0&size=10" `
        -Method Get `
        -Headers @{
            "Authorization" = "Bearer $nonAdminToken"
            "X-Tenant-ID" = $adminTenantId
        } `
        -ErrorAction Stop

    Write-Fail "Non-admin cannot access security audit (expect 403)" 200 "Should have been blocked"
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    if ($statusCode -eq 403) {
        Write-Success "Non-admin blocked from security audit (expect 403)" $statusCode
        Write-Info "Security check passed"
    } else {
        Write-Fail "Non-admin cannot access security audit (expect 403)" $statusCode $_
    }
}

# Try to access vendor audit logs as non-admin
try {
    $unauthorizedVendorResp = Invoke-WebRequest -Uri "$BaseURL/admin/audit/vendor?page=0&size=10" `
        -Method Get `
        -Headers @{
            "Authorization" = "Bearer $nonAdminToken"
            "X-Tenant-ID" = $adminTenantId
        } `
        -ErrorAction Stop

    Write-Fail "Non-admin cannot access vendor audit (expect 403)" 200 "Should have been blocked"
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    if ($statusCode -eq 403) {
        Write-Success "Non-admin blocked from vendor audit (expect 403)" $statusCode
        Write-Info "Security check passed"
    } else {
        Write-Fail "Non-admin cannot access vendor audit (expect 403)" $statusCode $_
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
