############################################################
#  TOKEN DIAGNOSTIC TEST
#  Check if token is valid and authentication is working
############################################################

$BaseURL = "http://localhost:8081/api"

Write-Host "============================================================"
Write-Host "  TOKEN DIAGNOSTIC TEST"
Write-Host "============================================================`n"

# Register user and get token
Write-Host "[STEP 1] Register user" -ForegroundColor Yellow
try {
    $email = "diag-$(Get-Random)@test.com"
    $registerResp = Invoke-WebRequest -Uri "$BaseURL/auth/register" `
        -Method Post `
        -UseBasicParsing `
        -Headers @{"Content-Type" = "application/json"} `
        -Body (ConvertTo-Json @{
            name = "Diag User"
            email = $email
            password = "TestPass123!@"
            confirmPassword = "TestPass123!@"
        }) `
        -ErrorAction Stop
    
    $regData = $registerResp.Content | ConvertFrom-Json
    
    Write-Host "  Response Status: $($registerResp.StatusCode)" -ForegroundColor Green
    Write-Host "  Response Body: $($registerResp.Content | ConvertFrom-Json | ConvertTo-Json -Depth 3)" -ForegroundColor Gray
    
    $token = $regData.accessToken
    Write-Host "`n  Token received: $($token.Substring(0, 50))..." -ForegroundColor Cyan
    
    if (-not $token) {
        Write-Host "  ERROR: No token in response!" -ForegroundColor Red
        exit 1
    }
    
} catch {
    Write-Host "  FAIL: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Try to use token with /auth/me
Write-Host "`n[STEP 2] Test /auth/me with token" -ForegroundColor Yellow
Write-Host "  Token: $($token.Substring(0, 50))..." -ForegroundColor Gray
Write-Host "  Header: Bearer $($token.Substring(0, 50))..." -ForegroundColor Gray

try {
    $meResp = Invoke-WebRequest -Uri "$BaseURL/auth/me" `
        -Method Get `
        -UseBasicParsing `
        -Headers @{
            "Authorization" = "Bearer $token"
            "Content-Type" = "application/json"
        } `
        -ErrorAction Stop
    
    Write-Host "  PASS (200)" -ForegroundColor Green
    Write-Host "  Response: $($meResp.Content)" -ForegroundColor Gray
    
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    $responseBody = $_.Exception.Response.GetResponseStream()
    $reader = New-Object System.IO.StreamReader($responseBody)
    $responseText = $reader.ReadToEnd()
    
    Write-Host "  FAIL ($statusCode)" -ForegroundColor Red
    Write-Host "  Error: $($_.Exception.Message)" -ForegroundColor Red
    Write-Host "  Response Body: $responseText" -ForegroundColor Red
}

# Try with different header format
Write-Host "`n[STEP 3] Test alternative header format" -ForegroundColor Yellow
try {
    $meResp = Invoke-WebRequest -Uri "$BaseURL/auth/me" `
        -Method Get `
        -UseBasicParsing `
        -Headers @{
            "Authorization" = $token  # Try without "Bearer"
            "Content-Type" = "application/json"
        } `
        -ErrorAction Stop
    
    Write-Host "  PASS (using token without Bearer)" -ForegroundColor Green
    
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "  FAIL ($statusCode) - also failed without Bearer" -ForegroundColor Red
}

Write-Host "`n============================================================`n"
