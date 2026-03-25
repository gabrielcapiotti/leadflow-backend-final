#!/usr/bin/env pwsh

$BaseUrl = "http://localhost:8081"

Write-Host "`n════════════════════════════════════════════════════════" -ForegroundColor Cyan
Write-Host "TESTE METRICS - WITH JWT TENANT EXTRACTION" -ForegroundColor Cyan
Write-Host "════════════════════════════════════════════════════════" -ForegroundColor Cyan

# Helper: Decode JWT
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

# Step 1: Register
Write-Host "`n[1] Registering user..." -ForegroundColor Yellow

$timestamp = Get-Date -Format "yyyyMMddHHmmss"
$testEmail = "metrics-test-$timestamp@leadflow.dev"
$testPassword = "TestPass123!@"

try {
    $regResp = Invoke-RestMethod -Uri "$BaseUrl/auth/register" `
        -Method POST `
        -ContentType "application/json" `
        -Body (@{
            name = "Metrics Test"
            email = $testEmail
            password = $testPassword
            confirmPassword = $testPassword
        } | ConvertTo-Json)
    
    $token = $regResp.accessToken
    Write-Host "✅ User registered: $testEmail" -ForegroundColor Green
} catch {
    Write-Host "❌ Registration failed: $_" -ForegroundColor Red
    exit 1
}

# Step 2: Decode JWT to extract tenant
Write-Host "`n[2] Decoding JWT..." -ForegroundColor Yellow

$jwtClaims = Decode-JWT $token

if ($jwtClaims) {
    Write-Host "✅ JWT decoded successfully" -ForegroundColor Green
    Write-Host "   Email: $($jwtClaims.sub)" -ForegroundColor Gray
    Write-Host "   Tenant: $($jwtClaims.tenant)" -ForegroundColor Gray
    Write-Host "   Role: $($jwtClaims.role)" -ForegroundColor Gray
    
    $tenant = $jwtClaims.tenant
} else {
    Write-Host "⚠️  JWT decode failed, using 'public'" -ForegroundColor Yellow
    $tenant = "public"
}

# Step 3: Test metrics endpoint WITH header
Write-Host "`n[3] Testing /api/vendor-leads/metrics..." -ForegroundColor Yellow
Write-Host "   Authorization: Bearer <token>" -ForegroundColor Gray
Write-Host "   X-Tenant-Id: $tenant" -ForegroundColor Gray

$headers = @{
    "Authorization" = "Bearer $token"
    "X-Tenant-Id" = $tenant
    "Content-Type" = "application/json"
}

try {
    $metricsResp = Invoke-RestMethod -Uri "$BaseUrl/api/vendor-leads/metrics" `
        -Method GET `
        -Headers $headers
    
    Write-Host "✅ Metrics endpoint SUCCESS (200)" -ForegroundColor Green
    Write-Host ($metricsResp | ConvertTo-Json -Depth 3) -ForegroundColor Yellow
} catch {
    $status = 0
    try { $status = $_.Exception.Response.StatusCode.value__ } catch {}
    
    Write-Host "❌ Metrics endpoint FAILED (HTTP $status)" -ForegroundColor Red
    Write-Host "   Error: $($_.Exception.Message)" -ForegroundColor DarkRed
    
    # Try to extract response body
    try {
        $reader = New-Object System.IO.StreamReader($_.Exception.Response.GetResponseStream())
        $body = $reader.ReadToEnd()
        Write-Host "   Response: $body" -ForegroundColor Yellow
    } catch {}
}

# Step 4: Test other endpoints for comparison
Write-Host "`n[4] Testing other endpoints for comparison..." -ForegroundColor Yellow

$endpoints = @(
    @{Name = "GET /api/leads"; Path = "/api/leads"; Method = "GET"},
    @{Name = "GET /vendor-leads"; Path = "/vendor-leads"; Method = "GET"}
)

foreach ($ep in $endpoints) {
    try {
        $resp = Invoke-RestMethod -Uri "$BaseUrl$($ep.Path)" `
            -Method $ep.Method `
            -Headers $headers
        
        Write-Host "✅ $($ep.Name) - OK" -ForegroundColor Green
    } catch {
        $status = 0
        try { $status = $_.Exception.Response.StatusCode.value__ } catch {}
        Write-Host "❌ $($ep.Name) - HTTP $status" -ForegroundColor Red
    }
}

Write-Host "`n════════════════════════════════════════════════════════" -ForegroundColor Cyan
