$BaseUrl = "http://localhost:8081"
$TenantId = "public"

# Use existing test user
$testEmail = "testuser_20260316192037@leadflow.com"
$testPassword = "TestPass@123456"

Write-Host "Testing PATCH /leads/{id}/status endpoint"
Write-Host "=========================================`n"

# Step 1: Login
Write-Host "Step 1: Login" -ForegroundColor Blue
$loginBody = @{
    email = $testEmail
    password = $testPassword
} | ConvertTo-Json

$loginResponse = Invoke-WebRequest -Uri "$BaseUrl/auth/login" `
    -Method POST `
    -Headers @{
        "Content-Type" = "application/json"
        "X-Tenant-ID" = $TenantId
    } `
    -Body $loginBody `
    -UseBasicParsing

$token = ($loginResponse.Content | ConvertFrom-Json).accessToken
Write-Host "[OK] Login successful"
Write-Host "Token: $($token.Substring(0, 50))...`n"

# Step 2: Get first lead
Write-Host "Step 2: Get leads" -ForegroundColor Blue
$authHeaders = @{
    "Authorization" = "Bearer $token"
    "Content-Type" = "application/json"
    "X-Tenant-ID" = $TenantId
}

$leadsResponse = Invoke-WebRequest -Uri "$BaseUrl/leads" `
    -Method GET `
    -Headers $authHeaders `
    -UseBasicParsing

$leads = $leadsResponse.Content | ConvertFrom-Json
$firstLeadId = $leads[0].id
Write-Host "[OK] Found lead: $firstLeadId`n"

# Step 3: Try different status parameter formats
Write-Host "Step 3: Test PATCH with status parameter" -ForegroundColor Blue
Write-Host "Current lead status: $($leads[0].status)`n"

# Format 1: Query parameter (CONTACTED - valid transition from NEW)
Write-Host "Attempt 1: ?status=CONTACTED (valid enum value)"
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/leads/$firstLeadId/status?status=CONTACTED" `
        -Method PATCH `
        -Headers $authHeaders `
        -UseBasicParsing
    Write-Host "Status: $($response.StatusCode)" -ForegroundColor Green
    $result = $response.Content | ConvertFrom-Json
    Write-Host "New status: $($result.status)`n"
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "Status Code: $($_.Exception.Response.StatusCode)`n"
}

# Format 2: Try lowercase valid value
Write-Host "Attempt 2: ?status=contacted (lowercase valid value)"
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/leads/$firstLeadId/status?status=contacted" `
        -Method PATCH `
        -Headers $authHeaders `
        -UseBasicParsing
    Write-Host "Status: $($response.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
}

# Format 3: Try QUALIFIED (valid value)
Write-Host "Attempt 3: ?status=QUALIFIED (valid transition from CONTACTED)"
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/leads/$firstLeadId/status?status=QUALIFIED" `
        -Method PATCH `
        -Headers $authHeaders `
        -UseBasicParsing
    Write-Host "Status: $($response.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
}

# Format 4: Try CLOSED
Write-Host "Attempt 4: ?status=CLOSED (valid transition from QUALIFIED)"
try {
    $response = Invoke-WebRequest -Uri "$BaseUrl/leads/$firstLeadId/status?status=CLOSED" `
        -Method PATCH `
        -Headers $authHeaders `
        -UseBasicParsing
    Write-Host "Status: $($response.StatusCode)" -ForegroundColor Green
} catch {
    Write-Host "Error: $($_.Exception.Message)" -ForegroundColor Red
}
