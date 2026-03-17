param()

$BaseUrl = "http://localhost:8081"
$TenantId = "public"
$Token = ""

Write-Host "=========================================="
Write-Host "LEADS ENDPOINTS - INTEGRATION TESTS"
Write-Host "=========================================="
Write-Host "Server: $BaseUrl"
Write-Host "Tenant: $TenantId`n"

# Helper function to get auth token
function Get-AuthToken {
    $loginBody = @{
        email = "carlos@leadflow.com"
        password = "SenhaForte@123"
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
        
        $json = $response.Content | ConvertFrom-Json
        return $json.accessToken
    } catch {
        Write-Host "Could not get auth token: $_" -ForegroundColor Yellow
        return $null
    }
}

# Try to get token
Write-Host "STEP 1: Getting Auth Token" -ForegroundColor Blue
Write-Host "====================="
$token = Get-AuthToken
if ($token) {
    Write-Host "[OK] Auth token obtained" -ForegroundColor Green
} else {
    Write-Host "[WARN] Could not obtain auth token, tests may fail" -ForegroundColor Yellow
}
Write-Host ""

# Define headers for authenticated requests
$authHeaders = @{
    "Content-Type" = "application/json"
    "X-Tenant-ID" = $TenantId
}
if ($token) {
    $authHeaders["Authorization"] = "Bearer $token"
}

# ENDPOINT 1: Create Lead (LeadController)
Write-Host "STEP 2: Create Lead (LeadController)" -ForegroundColor Blue
Write-Host "======================================"
$leadBody = @{
    name = "John Doe"
    email = "john@example.com"
    phone = "+5511999999999"
} | ConvertTo-Json

try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/api/leads" `
        -Method POST `
        -Headers $authHeaders `
        -Body $leadBody `
        -UseBasicParsing `
        -TimeoutSec 10
    
    $leadJson = $response.Content | ConvertFrom-Json
    $leadId = $leadJson.id
    Write-Host "[OK] Lead created - Status $($response.StatusCode)" -ForegroundColor Green
    Write-Host "    ID: $leadId"
} catch {
    Write-Host "[ERROR] Failed to create lead: $($_.Exception.Message)" -ForegroundColor Red
    $leadId = $null
}
Write-Host ""

# ENDPOINT 2: List Leads
Write-Host "STEP 3: List Leads (LeadController)" -ForegroundColor Blue
Write-Host "===================================="
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/api/leads" `
        -Method GET `
        -Headers $authHeaders `
        -UseBasicParsing `
        -TimeoutSec 10
    
    Write-Host "[OK] List leads - Status $($response.StatusCode)" -ForegroundColor Green
    $leads = $response.Content | ConvertFrom-Json
    Write-Host "    Found: $($leads.Count) leads"
} catch {
    Write-Host "[ERROR] Failed to list leads: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# ENDPOINT 3: List Vendor Leads (with pagination)
Write-Host "STEP 4: List Vendor Leads (VendorLeadController)" -ForegroundColor Blue
Write-Host "================================================"
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/vendor-leads?page=0&size=10" `
        -Method GET `
        -Headers $authHeaders `
        -UseBasicParsing `
        -TimeoutSec 10
    
    Write-Host "[OK] List vendor leads - Status $($response.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Failed to list vendor leads: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# ENDPOINT 5: Get Vendor Lead Metrics
Write-Host "STEP 5: Get Vendor Lead Metrics" -ForegroundColor Blue
Write-Host "================================"
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/vendor-leads/metrics" `
        -Method GET `
        -Headers $authHeaders `
        -UseBasicParsing `
        -TimeoutSec 10
    
    Write-Host "[OK] Get metrics - Status $($response.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Failed to get metrics: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# ENDPOINT 6: Get Vendor Lead Ranking
Write-Host "STEP 6: Get Vendor Lead Ranking" -ForegroundColor Blue
Write-Host "================================"
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/vendor-leads/ranking" `
        -Method GET `
        -Headers $authHeaders `
        -UseBasicParsing `
        -TimeoutSec 10
    
    Write-Host "[OK] Get ranking - Status $($response.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Failed to get ranking: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# ENDPOINT 7: Get Stage Time Metrics
Write-Host "STEP 7: Get Stage Time Metrics" -ForegroundColor Blue
Write-Host "==============================="
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/vendor-leads/metrics/stage-time" `
        -Method GET `
        -Headers $authHeaders `
        -UseBasicParsing `
        -TimeoutSec 10
    
    Write-Host "[OK] Get stage time metrics - Status $($response.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Failed to get stage time metrics: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

# ENDPOINT 8: Get Conversion Metrics
Write-Host "STEP 8: Get Conversion Metrics" -ForegroundColor Blue
Write-Host "==============================="
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/vendor-leads/metrics/conversion" `
        -Method GET `
        -Headers $authHeaders `
        -UseBasicParsing `
        -TimeoutSec 10
    
    Write-Host "[OK] Get conversion metrics - Status $($response.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "[ERROR] Failed to get conversion metrics: $($_.Exception.Message)" -ForegroundColor Red
}
Write-Host ""

Write-Host "=========================================="
Write-Host "LEADS TESTS COMPLETED" -ForegroundColor Cyan
Write-Host "=========================================="
