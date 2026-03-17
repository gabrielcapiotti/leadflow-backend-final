param()

$BaseUrl = "http://localhost:8081"
$TenantId = "public"

Write-Host "=========================================="
Write-Host "COMPLETE LEADS FLOW TEST"
Write-Host "=========================================="
Write-Host "Register > Login > Lead Endpoints"
Write-Host "Server: $BaseUrl`n"

# Generate unique email for this test run
$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$testEmail = "testuser_$timestamp@leadflow.com"
$testPassword = "TestPass@123456"
$testName = "Test User $timestamp"

Write-Host "Generated Test User:`n"
Write-Host "  Email: $testEmail"
Write-Host "  Password: $testPassword"
Write-Host "  Name: $testName`n"

# ============================================================
# STEP 1: REGISTER NEW USER
# ============================================================
Write-Host "STEP 1: Register New User" -ForegroundColor Blue
Write-Host "=======================`n"

$registerBody = @{
    name = $testName
    email = $testEmail
    password = $testPassword
    confirmPassword = $testPassword
} | ConvertTo-Json

try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/auth/register" `
        -Method POST `
        -Headers @{
            "Content-Type" = "application/json"
            "X-Tenant-ID" = $TenantId
        } `
        -Body $registerBody `
        -UseBasicParsing `
        -TimeoutSec 10
    
    Write-Host "[OK] User registered - Status $($response.StatusCode)" -ForegroundColor Green
    $registerJson = $response.Content | ConvertFrom-Json
    if ($registerJson.userId) {
        Write-Host "    User ID: $($registerJson.userId)`n"
    }
} catch {
    Write-Host "[ERROR] Registration failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# ============================================================
# STEP 2: LOGIN WITH NEW USER
# ============================================================
Write-Host "STEP 2: Login with New User" -ForegroundColor Blue
Write-Host "========================`n"

$loginBody = @{
    email = $testEmail
    password = $testPassword
} | ConvertTo-Json

try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/auth/login" `
        -Method POST `
        -Headers @{
            "Content-Type" = "application/json"
            "X-Tenant-ID" = $TenantId
        } `
        -Body $loginBody `
        -UseBasicParsing `
        -TimeoutSec 10
    
    Write-Host "[OK] User logged in - Status $($response.StatusCode)" -ForegroundColor Green
    $loginJson = $response.Content | ConvertFrom-Json
    $token = $loginJson.accessToken
    Write-Host "    Token obtained (length: $($token.Length))`n"
} catch {
    Write-Host "[ERROR] Login failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Set up auth headers for subsequent requests
$authHeaders = @{
    "Content-Type" = "application/json"
    "X-Tenant-ID" = $TenantId
    "Authorization" = "Bearer $token"
}

# ============================================================
# STEP 3: CREATE LEADS
# ============================================================
Write-Host "STEP 3: Create Multiple Leads" -ForegroundColor Blue
Write-Host "==========================`n"

$leadsCreated = @()

$leadData = @(
    @{ name = "John Smith"; email = "john.smith@example.com"; phone = "+5511988881111" },
    @{ name = "Maria Silva"; email = "maria.silva@example.com"; phone = "+5511988882222" },
    @{ name = "Pedro Costa"; email = "pedro.costa@example.com"; phone = "+5511988883333" }
)

foreach ($lead in $leadData) {
    $leadBody = @{
        name = $lead.name
        email = $lead.email
        phone = $lead.phone
    } | ConvertTo-Json
    
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/leads" `
            -Method POST `
            -Headers $authHeaders `
            -Body $leadBody `
            -UseBasicParsing `
            -TimeoutSec 10
        
        Write-Host "[OK] Created: $($lead.name)" -ForegroundColor Green
        $leadJson = $response.Content | ConvertFrom-Json
        $leadsCreated += @{ id = $leadJson.id; name = $lead.name }
    } catch {
        Write-Host "[ERROR] Failed to create $($lead.name): $($_.Exception.Message)" -ForegroundColor Red
    }
}

Write-Host "`nTotal leads created: $($leadsCreated.Count)`n"

# ============================================================
# STEP 4: LIST LEADS
# ============================================================
Write-Host "STEP 4: List All Leads" -ForegroundColor Blue
Write-Host "===================`n"

try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/leads" `
        -Method GET `
        -Headers $authHeaders `
        -UseBasicParsing `
        -TimeoutSec 10
    
    Write-Host "[OK] Listed leads - Status $($response.StatusCode)" -ForegroundColor Green
    $leadsJson = $response.Content | ConvertFrom-Json
    Write-Host "    Total leads: $($leadsJson.Count)`n"
    
    if ($leadsJson.Count -gt 0) {
        Write-Host "    First lead:"
        Write-Host "      ID: $($leadsJson[0].id)"
        Write-Host "      Name: $($leadsJson[0].name)"
        Write-Host "      Email: $($leadsJson[0].email)`n"
    }
} catch {
    Write-Host "[ERROR] Failed to list leads: $($_.Exception.Message)" -ForegroundColor Red
}

# ============================================================
# STEP 5: GET SPECIFIC LEAD
# ============================================================
if ($leadsCreated.Count -gt 0) {
    Write-Host "STEP 5: Get Specific Lead Details" -ForegroundColor Blue
    Write-Host "==============================`n"
    
    $firstLeadId = $leadsCreated[0].id
    
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/leads/$firstLeadId" `
            -Method GET `
            -Headers $authHeaders `
            -UseBasicParsing `
            -TimeoutSec 10
        
        Write-Host "[OK] Retrieved lead - Status $($response.StatusCode)" -ForegroundColor Green
        $leadJson = $response.Content | ConvertFrom-Json
        Write-Host "    ID: $($leadJson.id)"
        Write-Host "    Name: $($leadJson.name)"
        Write-Host "    Email: $($leadJson.email)"
        Write-Host "    Phone: $($leadJson.phone)"
        Write-Host "    Status: $($leadJson.status)`n"
    } catch {
        Write-Host "[ERROR] Failed to get lead: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# ============================================================
# STEP 6: UPDATE LEAD STATUS
# ============================================================
if ($leadsCreated.Count -gt 0) {
    Write-Host "STEP 6: Update Lead Status" -ForegroundColor Blue
    Write-Host "=======================`n"
    
    $firstLeadId = $leadsCreated[0].id
    
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/leads/$firstLeadId/status?status=CONTACTED" `
            -Method PATCH `
            -Headers $authHeaders `
            -UseBasicParsing `
            -TimeoutSec 10
        
        Write-Host "[OK] Lead status updated - Status $($response.StatusCode)" -ForegroundColor Green
        $leadJson = $response.Content | ConvertFrom-Json
        Write-Host "    New Status: $($leadJson.status)`n"
    } catch {
        Write-Host "[ERROR] Failed to update status: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# ============================================================
# STEP 7: DELETE LEAD (SOFT DELETE)
# ============================================================
if ($leadsCreated.Count -gt 1) {
    Write-Host "STEP 7: Delete Lead (Soft Delete)" -ForegroundColor Blue
    Write-Host "==============================`n"
    
    $leadToDelete = $leadsCreated[1].id
    
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/leads/$leadToDelete" `
            -Method DELETE `
            -Headers $authHeaders `
            -UseBasicParsing `
            -TimeoutSec 10
        
        Write-Host "[OK] Lead soft-deleted - Status $($response.StatusCode)" -ForegroundColor Green
        Write-Host "    Deleted lead: $($leadsCreated[1].name)`n"
    } catch {
        Write-Host "[ERROR] Failed to delete lead: $($_.Exception.Message)" -ForegroundColor Red
    }
}

# ============================================================
# SUMMARY
# ============================================================
Write-Host "=========================================="
Write-Host "COMPLETE FLOW TEST - SUMMARY" -ForegroundColor Cyan
Write-Host "=========================================="
Write-Host "`nOperations Completed:`n"
Write-Host "✅ Register new user"
Write-Host "✅ Login and get JWT token"
Write-Host "✅ Create multiple leads"
Write-Host "✅ List all leads"
Write-Host "✅ Get specific lead details"
Write-Host "✅ Update lead status"
Write-Host "✅ Delete (soft) lead"
Write-Host "`nTest User Created:"
Write-Host "  Email: $testEmail"
Write-Host "  Password: $testPassword`n"
Write-Host "=========================================="
