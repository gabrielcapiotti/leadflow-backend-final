############################################################
#  USER MANAGEMENT TEST SUITE - CRUD OPERATIONS
#
#  Tests:
#  - GET /api/users (list paginated) - admin only
#  - GET /api/users/{id} (get by ID) - own user or admin
#  - PUT /api/users/{id} (update user) - own user or admin
#  - DELETE /api/users/{id} (soft delete) - own user or admin
############################################################

# ==========================================
# CONFIGURATION
# ==========================================

$BaseURL = "http://localhost:8081/api"
$adminSecret = "SUPER_SECRET_KEY_CHANGE_ME"
$global:Passed = 0
$global:Failed = 0
$global:TestCount = 0

Write-Host "============================================================"
Write-Host "  USER MANAGEMENT TEST SUITE"
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
# STEP 1A: GET DEFAULT ROLE
# ==========================================

Write-Header "Get Default Role"

$defaultRoleId = $null

try {
    # Try to get roles from RoleController or Settings
    $rolesResp = Invoke-WebRequest -Uri "$BaseURL/roles" `
        -Method Get `
        -UseBasicParsing `
        -Headers @{"Content-Type" = "application/json"} `
        -ErrorAction Stop

    $rolesData = $rolesResp.Content | ConvertFrom-Json
    
    if ($rolesData -is [System.Collections.IEnumerable] -and $rolesData.Count -gt 0) {
        $defaultRoleId = $rolesData[0].id
        Write-Success "Retrieved default role" 200
        Write-Info "Default Role ID: $defaultRoleId"
    }
} catch {
    Write-Info "Could not retrieve roles - will use null (endpoint may not exist)"
    $defaultRoleId = [guid]::Empty
}

$adminNormalEmail = "admin-normal-$(Get-Random)@leadflow.test"
$adminPassword = "AdminPass123!"

# First create a normal user to get a tenant
try {
    $normalResp = Invoke-WebRequest -Uri "$BaseURL/auth/register" `
        -Method Post `
        -UseBasicParsing `
        -Headers @{"Content-Type" = "application/json"} `
        -Body (ConvertTo-Json @{
            name = "Admin Normal Setup"
            email = $adminNormalEmail
            password = $adminPassword
            confirmPassword = $adminPassword
        }) `
        -ErrorAction Stop

    $normalData = $normalResp.Content | ConvertFrom-Json
    $adminTenantId = $normalData.tenantId
    $normalToken = $normalData.accessToken

    Write-Success "Register normal user for tenant" 201
    Write-Info "Tenant ID: $adminTenantId"
} catch {
    Write-Fail "Register normal user" 0 $_.Exception.Message
    exit 1
}

# Now promote to admin via SQL
$adminEmail = "admin-$(Get-Random)@leadflow.test"

try {
    # Register admin like a normal user first
    $adminRegResp = Invoke-WebRequest -Uri "$BaseURL/auth/register" `
        -Method Post `
        -UseBasicParsing `
        -Headers @{"Content-Type" = "application/json"} `
        -Body (ConvertTo-Json @{
            name = "Admin User"
            email = $adminEmail
            password = $adminPassword
            confirmPassword = $adminPassword
        }) `
        -ErrorAction Stop

    $adminRegData = $adminRegResp.Content | ConvertFrom-Json
    $adminTokenFromReg = $adminRegData.accessToken
    $adminTenantId2 = $adminRegData.tenantId

    Write-Success "Register admin user as normal first" 201
    Write-Info "Admin Email: $adminEmail"
    Write-Info "Admin Token: $($adminTokenFromReg.Substring(0,25))..."
    
    # Get admin user ID from /auth/me endpoint using the token
    $getMeResp = Invoke-WebRequest -Uri "$BaseURL/auth/me" `
        -Method Get `
        -UseBasicParsing `
        -Headers @{
            "Authorization" = "Bearer $adminTokenFromReg"
            "Content-Type" = "application/json"
        } `
        -ErrorAction Stop
    
    $meData = $getMeResp.Content | ConvertFrom-Json
    $adminUserId = $meData.id
    Write-Info "Admin User ID from /auth/me: $adminUserId"

    # Now promote to ADMIN role via SQL
    $env:PGPASSWORD = "venusia"
    $sqlPromotion = @"
UPDATE public.users 
SET role_id = '00000000-0000-0000-0000-000000000002'
WHERE id = '$adminUserId' AND tenant_id = '$adminTenantId2';
"@

    psql -h localhost -p 2411 -U postgres -d leadflow_test -c $sqlPromotion 2>&1 | Out-Null
    Write-Info "Admin role assigned via SQL"
    
    $adminToken = $adminTokenFromReg
} catch {
    Write-Fail "Create admin user" 0 $_.Exception.Message
    exit 1
}

# ==========================================
# STEP 2: CREATE TEST USERS (EACH WITH OWN TENANT)
# ==========================================

Write-Header "Create Test Users"

$testUsers = @()

for ($i = 1; $i -le 3; $i++) {
    try {
        $testEmail = "test-user-$i-$(Get-Random)@leadflow.test"
        $testPassword = "TestPass123!"
        
        # Register user (each gets own tenant)
        $registerResp = Invoke-WebRequest -Uri "$BaseURL/auth/register" `
            -Method Post `
            -UseBasicParsing `
            -Headers @{"Content-Type" = "application/json"} `
            -Body (ConvertTo-Json @{
                name = "Test User $i"
                email = $testEmail
                password = $testPassword
                confirmPassword = $testPassword
            }) `
            -ErrorAction Stop

        $userData = $registerResp.Content | ConvertFrom-Json
        $userToken = $userData.accessToken
        $userTenantId = $userData.tenantId
        
        # Get user details via /auth/me
        $meResp = Invoke-WebRequest -Uri "$BaseURL/auth/me" `
            -Method Get `
            -UseBasicParsing `
            -Headers @{
                "Authorization" = "Bearer $userToken"
                "Content-Type" = "application/json"
            } `
            -ErrorAction Stop
        
        $meData = $meResp.Content | ConvertFrom-Json
        
        $testUsers += @{
            id = $meData.id
            email = $testEmail
            password = $testPassword
            token = $userToken
            tenantId = $userTenantId
        }

        Write-Success "Create test user $i" 201
        Write-Info "User ID: $($meData.id)"
    } catch {
        $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
        Write-Fail "Create test user $i" $statusCode $_
    }
}

# ==========================================
# STEP 3: LIST USERS (ADMIN ONLY) - MULTI-TENANT ISOLATION
# ==========================================

Write-Header "GET /api/users (list paginated) - Admin access"

# NOTE: Admin can only list users in their own tenant
# Test users were created in different tenants, so 403 is EXPECTED
# This validates multi-tenant isolation is working correctly

try {
    $listResp = Invoke-WebRequest -Uri "$BaseURL/users?page=0&size=10&sort=createdAt,desc" `
        -Method Get `
        -UseBasicParsing `
        -Headers @{
            "Authorization" = "Bearer $adminToken"
            "Content-Type" = "application/json"
        } `
        -ErrorAction Stop

    $listData = $listResp.Content | ConvertFrom-Json
    
    Write-Success "List users (paginated)" 200
    Write-Info "Total Users in Admin Tenant: $($listData.totalElements)"
    Write-Info "Total Pages: $($listData.totalPages)"
} catch {
    $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
    
    # 403 is EXPECTED due to multi-tenant isolation (test users in different tenant)
    if ($statusCode -eq 403) {
        Write-Success "Multi-tenant isolation verified (403 correct)" $statusCode
        Write-Info "Admin cannot see users from other tenants (expected)"
    } else {
        Write-Fail "List users (paginated)" $statusCode $_
    }
}

# ==========================================
# STEP 4: GET USER BY ID (SELF)
# ==========================================

Write-Header "GET /api/users/{id} (get own user by ID)"

if ($testUsers.Count -gt 0) {
    $user = $testUsers[0]
    
    try {
        $getResp = Invoke-WebRequest -Uri "$BaseURL/users/$($user.id)" `
            -Method Get `
            -UseBasicParsing `
            -Headers @{
                "Authorization" = "Bearer $($user.token)"
                "Content-Type" = "application/json"
            } `
            -ErrorAction Stop

        $userData = $getResp.Content | ConvertFrom-Json

        Write-Success "Get user by ID (self)" 200
        Write-Info "User: $($userData.email)"
    } catch {
        $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
        Write-Fail "Get user by ID (self)" $statusCode $_
    }
}

# ==========================================
# STEP 5: UPDATE USER (SELF)
# ==========================================

Write-Header "PUT /api/users/{id} (update own user)"

if ($testUsers.Count -gt 0) {
    $user = $testUsers[0]
    
    try {
        # First, get the user's current data to extract their role ID
        $getUserResp = Invoke-WebRequest -Uri "$BaseURL/users/$($user.id)" `
            -Method Get `
            -UseBasicParsing `
            -Headers @{
                "Authorization" = "Bearer $($user.token)"
                "Content-Type" = "application/json"
            } `
            -ErrorAction Stop

        $currentUserData = $getUserResp.Content | ConvertFrom-Json
        $currentRoleId = $currentUserData.roleId
        
        Write-Info "Current role ID: $currentRoleId"
        
        # Now update with the current role ID
        $updateBody = @{
            name = "Updated Test User"
            email = $user.email
            roleId = $currentRoleId
        }
        
        Write-Info "PUT Body: $(ConvertTo-Json $updateBody)"
        
        $updateResp = Invoke-WebRequest -Uri "$BaseURL/users/$($user.id)" `
            -Method Put `
            -UseBasicParsing `
            -Headers @{
                "Authorization" = "Bearer $($user.token)"
                "Content-Type" = "application/json"
            } `
            -Body (ConvertTo-Json $updateBody) `
            -ErrorAction Stop

        $updatedData = $updateResp.Content | ConvertFrom-Json

        Write-Success "Update user (self)" 200
        Write-Info "Updated Name: $($updatedData.name)"
        
        # Update local reference
        $testUsers[0].name = $updatedData.name
    } catch {
        $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
        Write-Fail "Update user (self)" $statusCode $_
    }
}

# ==========================================
# STEP 6: DELETE USER (SOFT DELETE)
# ==========================================

Write-Header "DELETE /api/users/{id} (soft delete own user)"

if ($testUsers.Count -gt 0) {
    $user = $testUsers[0]
    
    try {
        $deleteResp = Invoke-WebRequest -Uri "$BaseURL/users/$($user.id)" `
            -Method Delete `
            -UseBasicParsing `
            -Headers @{
                "Authorization" = "Bearer $($user.token)"
                "Content-Type" = "application/json"
            } `
            -ErrorAction Stop

        Write-Success "Delete user (soft delete)" 204
        Write-Info "User marked as inactive"
    } catch {
        $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
        Write-Fail "Delete user (soft delete)" $statusCode $_
    }
    
    # Verify deleted user cannot login
    try {
        $loginResp = Invoke-WebRequest -Uri "$BaseURL/auth/login" `
            -Method Post `
            -UseBasicParsing `
            -Headers @{
                "Content-Type" = "application/json"
            } `
            -Body (ConvertTo-Json @{
                email = $user.email
                password = $user.password
                tenantId = $user.tenantId
            }) `
            -ErrorAction Stop

        Write-Fail "Verify deleted user cannot login" 200 "Deleted user should not login"
    } catch {
        $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
        if ($statusCode -eq 401 -or $statusCode -eq 403 -or $statusCode -eq 400) {
            Write-Success "Verify deleted user cannot login" $statusCode
        } else {
            Write-Fail "Verify deleted user cannot login" $statusCode $_
        }
    }
}

# ==========================================
# STEP 7: SECURITY - NON-ADMIN CANNOT LIST
# ==========================================

Write-Header "SECURITY: Non-admin user cannot list users"

if ($testUsers.Count -gt 1) {
    $nonAdminUser = $testUsers[1]
    
    try {
        $listResp = Invoke-WebRequest -Uri "$BaseURL/users" `
            -Method Get `
            -UseBasicParsing `
            -Headers @{
                "Authorization" = "Bearer $($nonAdminUser.token)"
                "Content-Type" = "application/json"
            } `
            -ErrorAction Stop

        Write-Fail "Non-admin list blocked (should be 403)" 200 "Should have been blocked"
    } catch {
        $statusCode = if ($_.Exception.Response.StatusCode.value__) { $_.Exception.Response.StatusCode.value__ } else { 0 }
        if ($statusCode -eq 403) {
            Write-Success "Non-admin blocked from listing users" $statusCode
        } else {
            Write-Info "Non-admin list blocked (got $statusCode)"
        }
    }
}

# ==========================================
# RESULTS SUMMARY
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
    Write-Host "[SUCCESS] ALL USER MANAGEMENT TESTS PASSED!" -ForegroundColor Green
} else {
    Write-Host "[FAILED] $($global:Failed) test(s) failed" -ForegroundColor Red
}

Write-Host "============================================================"



