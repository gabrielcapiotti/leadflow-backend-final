param()

$BaseUrl = "http://localhost:8081"
$TenantId = "public"

Write-Host "=========================================="
Write-Host "TESTING LEAD STATUS HISTORY ENDPOINTS"
Write-Host "=========================================="
Write-Host ""

# Test user credentials
$testEmail = "testuser_20260316193104@leadflow.com"
$testPassword = "TestPass@123456"

Write-Host "Step 1: Login" -ForegroundColor Blue
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
    
    $token = ($response.Content | ConvertFrom-Json).accessToken
    Write-Host "[OK] Login successful`n"
} catch {
    Write-Host "[ERROR] Login failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Prepare auth headers
$authHeaders = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
    "X-Tenant-ID" = $TenantId
}

# Step 2: Create a new lead
Write-Host "Step 2: Create new lead" -ForegroundColor Blue
$leadBody = @{
    name = "Test Lead for History"
    email = "testlead_$(Get-Date -Format 'yyyyMMddHHmmss')@example.com"
    phone = "+5511999999999"
} | ConvertTo-Json

try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/leads" `
        -Method POST `
        -Headers $authHeaders `
        -Body $leadBody `
        -UseBasicParsing
    
    $leadData = $response.Content | ConvertFrom-Json
    $leadId = $leadData.id
    Write-Host "[OK] Created lead: $leadId"
    Write-Host "    Initial status: $($leadData.status)`n"
} catch {
    Write-Host "[ERROR] Failed to create lead: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 3: Update lead status to create history
Write-Host "Step 3: Update lead status multiple times to create history" -ForegroundColor Blue
try {
    # First update: NEW -> CONTACTED
    Invoke-WebRequest -Uri "$BaseUrl/leads/$leadId/status?status=CONTACTED" `
        -Method PATCH `
        -Headers $authHeaders `
        -UseBasicParsing | Out-Null
    Write-Host "[OK] Updated to CONTACTED"
    
    # Second update: CONTACTED -> QUALIFIED
    Invoke-WebRequest -Uri "$BaseUrl/leads/$leadId/status?status=QUALIFIED" `
        -Method PATCH `
        -Headers $authHeaders `
        -UseBasicParsing | Out-Null
    Write-Host "[OK] Updated to QUALIFIED"
    
    # Third update: QUALIFIED -> CLOSED
    Invoke-WebRequest -Uri "$BaseUrl/leads/$leadId/status?status=CLOSED" `
        -Method PATCH `
        -Headers $authHeaders `
        -UseBasicParsing | Out-Null
    Write-Host "[OK] Updated to CLOSED`n"
} catch {
    Write-Host "[ERROR] Failed to update status: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 4: Get the entire history for this lead
Write-Host "Step 4: GET /leads/{leadId}/history" -ForegroundColor Blue
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/leads/$leadId/history" `
        -Method GET `
        -Headers $authHeaders `
        -UseBasicParsing
    
    $history = $response.Content | ConvertFrom-Json
    Write-Host "[OK] Status: $($response.StatusCode)" -ForegroundColor Green
    Write-Host "    Records found: $($history.Count)"
    
    if ($history.Count -gt 0) {
        Write-Host "    History details:"
        for ($i = 0; $i -lt $history.Count; $i++) {
            $record = $history[$i]
            Write-Host "      [$i] Status: $($record.status) at $($record.changedAt) (by: $($record.changedBy))"
        }
        
        # Save first history ID for next test
        $historyId = $history[0].id
        Write-Host "    Saved history ID: $historyId`n"
    }
} catch {
    Write-Host "[ERROR] Failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 5: Get specific history record by ID
if ($historyId) {
    Write-Host "Step 5: GET /leads/history/{historyId}" -ForegroundColor Blue
    try {
        $response = Invoke-WebRequest -Uri "$BaseUrl/leads/history/$historyId" `
            -Method GET `
            -Headers $authHeaders `
            -UseBasicParsing
        
        $record = $response.Content | ConvertFrom-Json
        Write-Host "[OK] Status: $($response.StatusCode)" -ForegroundColor Green
        Write-Host "    Status: $($record.status)"
        Write-Host "    Changed at: $($record.changedAt)"
        Write-Host "    Changed by: $($record.changedBy)`n"
    } catch {
        Write-Host "[ERROR] Failed: $($_.Exception.Message)" -ForegroundColor Red
    }
} else {
    Write-Host "Step 5: SKIPPED (no history ID available)" -ForegroundColor Yellow
}

Write-Host "=========================================="
Write-Host "LEAD STATUS HISTORY TEST - COMPLETE"
Write-Host "=========================================="
