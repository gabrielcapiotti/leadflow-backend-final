#!/usr/bin/env pwsh

$baseUrl = "http://localhost:8081"

Write-Host "`n════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "TESTE: Vendor Auto-Creation on First Login" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════" -ForegroundColor Cyan

# Step 1: Register new user
Write-Host "`n[1/5] Registering new user..." -ForegroundColor Yellow

$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$testEmail = "vendor-test-$timestamp@leadflow.dev"
$testPassword = "TestPass123!@"

$headers = @{"Content-Type" = "application/json"}

try {
    $regResp = Invoke-RestMethod -Uri "$baseUrl/auth/register" `
        -Method POST `
        -Headers $headers `
        -Body (@{
            email = $testEmail
            password = $testPassword
            confirmPassword = $testPassword
            name = "Vendor Test"
        } | ConvertTo-Json)
    
    Write-Host "✅ User registered: $testEmail" -ForegroundColor Green
} catch {
    Write-Host "❌ Registration failed: $_" -ForegroundColor Red
    $errorResponse = $_.Exception.Response
    if ($errorResponse) {
        $reader = [System.IO.StreamReader]::new($errorResponse.GetResponseStream())
        $body = $reader.ReadToEnd()
        Write-Host "Response: $body" -ForegroundColor Yellow
    }
    exit 1
}

# Step 2: Login (this should trigger vendor auto-creation)
Write-Host "`n[2/5] Logging in (should trigger vendor auto-creation)..." -ForegroundColor Yellow

try {
    $loginResp = Invoke-RestMethod -Uri "$baseUrl/auth/login" `
        -Method POST `
        -Headers $headers `
        -Body (@{
            email = $testEmail
            password = $testPassword
        } | ConvertTo-Json)
    
    $token = $loginResp.accessToken
    Write-Host "✅ Login successful" -ForegroundColor Green
    Write-Host "   Token received: $($token.Substring(0, 20))..." -ForegroundColor Gray
} catch {
    Write-Host "❌ Login failed: $($_.Exception.Message)" -ForegroundColor Red
    $errorResponse = $_.Exception.Response
    if ($errorResponse) {
        $reader = [System.IO.StreamReader]::new($errorResponse.GetResponseStream())
        $body = $reader.ReadToEnd()
        Write-Host "Response: $body" -ForegroundColor Yellow
    }
    exit 1
}

# Step 3: Decode JWT to get tenant
Write-Host "`n[3/5] Extracting tenant from JWT..." -ForegroundColor Yellow

function Decode-JWT {
    param([string]$Token)
    
    $parts = $Token.Split('.')
    if ($parts.Length -ne 3) { return $null }
    
    $payload = $parts[1]
    $padding = 4 - ($payload.Length % 4)
    if ($padding -ne 4) { $payload += "=" * $padding }
    
    try {
        $decoded = [System.Text.Encoding]::UTF8.GetString([System.Convert]::FromBase64String($payload))
        return $decoded | ConvertFrom-Json
    } catch {
        return $null
    }
}

$jwtClaims = Decode-JWT $token
if ($jwtClaims) {
    $tenant = $jwtClaims.tenant
    Write-Host "✅ Tenant extracted: $tenant" -ForegroundColor Green
    Write-Host "   User ID: $($jwtClaims.userId)" -ForegroundColor Gray
} else {
    Write-Host "❌ Failed to decode JWT" -ForegroundColor Red
    exit 1
}

# Step 4: Test /api/vendor-leads/metrics endpoint
Write-Host "`n[4/5] Testing /api/vendor-leads/metrics endpoint..." -ForegroundColor Yellow

$authHeaders = @{
    "Authorization" = "Bearer $token"
    "X-Tenant-Id" = $tenant
    "Content-Type" = "application/json"
}

try {
    $metricsResp = Invoke-RestMethod -Uri "$baseUrl/api/vendor-leads/metrics" `
        -Method GET `
        -Headers $authHeaders
    
    Write-Host "✅ /api/vendor-leads/metrics SUCCESS (HTTP 200)" -ForegroundColor Green
    Write-Host "   Response: " -ForegroundColor Gray
    $metricsResp | ConvertTo-Json | Write-Host -ForegroundColor Gray
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "❌ /api/vendor-leads/metrics FAILED (HTTP $statusCode)" -ForegroundColor Red
    Write-Host "   Error: $($_.ErrorDetails.Message)" -ForegroundColor Red
}

# Step 5: Test /api/leads endpoint
Write-Host "`n[5/5] Testing /api/leads endpoint..." -ForegroundColor Yellow

try {
    $leadsResp = Invoke-RestMethod -Uri "$baseUrl/api/leads" `
        -Method GET `
        -Headers $authHeaders
    
    Write-Host "✅ /api/leads SUCCESS (HTTP 200)" -ForegroundColor Green
    Write-Host "   Leads count: $($leadsResp.Count)" -ForegroundColor Gray
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "❌ /api/leads FAILED (HTTP $statusCode)" -ForegroundColor Red
}

Write-Host "`n════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "TESTE CONCLUÍDO" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════" -ForegroundColor Cyan
