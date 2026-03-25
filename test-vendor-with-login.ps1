#!/usr/bin/env pwsh

$baseUrl = "http://localhost:8081"

Write-Host "`n════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "TESTE: Vendor Auto-Creation Flow" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════" -ForegroundColor Cyan

$email = "user-$(Get-Random 10000)@leadflow.dev"
$password = "Test@Pass123"

# Step 1: Register
Write-Host "`n[1/3] Registering user: $email" -ForegroundColor Yellow
try {
    $regResp = Invoke-RestMethod -Uri "$baseUrl/auth/register" `
        -Method POST `
        -Headers @{"Content-Type"="application/json"} `
        -Body (@{
            email = $email
            password = $password
            confirmPassword = $password
            name = "Test User"
        } | ConvertTo-Json)
    
    Write-Host "✅ User registered" -ForegroundColor Green
    Write-Host "   Email: $email" -ForegroundColor Gray
} catch {
    Write-Host "❌ Registration failed: $($_.Exception.Message)" -ForegroundColor Red
    exit 1
}

# Step 2: Login (this triggers vendor auto-creation via authenticateUser)
Write-Host "`n[2/3] Logging in (triggers vendor auto-creation)..." -ForegroundColor Yellow
try {
    $loginResp = Invoke-RestMethod -Uri "$baseUrl/auth/login" `
        -Method POST `
        -Headers @{"Content-Type"="application/json"} `
        -Body (@{
            email = $email
            password = $password
        } | ConvertTo-Json)
    
    $token = $loginResp.accessToken
    Write-Host "✅ Login successful" -ForegroundColor Green
    Write-Host "   Token: $($token.Substring(0, 30))..." -ForegroundColor Gray
} catch {
    Write-Host "❌ Login failed: $($_.Exception.Message)" -ForegroundColor Red
    $errorResponse = $_.Exception.Response
    if ($errorResponse) {
        $reader = [System.IO.StreamReader]::new($errorResponse.GetResponseStream())
        $body = $reader.ReadToEnd()
        Write-Host "Response body: $body" -ForegroundColor Yellow
    }
    exit 1
}

# Step 3: Test metrics endpoint
Write-Host "`n[3/3] Testing /api/vendor-leads/metrics..." -ForegroundColor Yellow
try {
    $metricsResp = Invoke-RestMethod -Uri "$baseUrl/api/vendor-leads/metrics" `
        -Method GET `
        -Headers @{
            "Authorization" = "Bearer $token"
            "X-Tenant-Id" = "public"
        }
    
    Write-Host "✅ SUCCESS: /api/vendor-leads/metrics returned 200!" -ForegroundColor Green
    Write-Host "   Response:" -ForegroundColor Gray
    $metricsResp | ConvertTo-Json | Write-Host -ForegroundColor Gray
} catch {
    $statusCode = $_.Exception.Response.StatusCode.value__
    Write-Host "❌ FAILED: /api/vendor-leads/metrics returned HTTP $statusCode" -ForegroundColor Red
    Write-Host "   Error: $($_.Exception.Message)" -ForegroundColor Red
}

Write-Host "`n════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "TEST COMPLETE" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════" -ForegroundColor Cyan
